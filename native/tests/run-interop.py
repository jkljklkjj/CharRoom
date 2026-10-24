#!/usr/bin/env python3
"""Linux JNI/Netty interoperability test with synthetic identities; never starts the application stack."""
import argparse
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-report', type=Path, required=True, help='Existing backend Surefire XML containing its test classpath')
    parser.add_argument('--probe-bundle', type=Path, required=True, help='Output of test-message-protocol.ps1 -JniProbeOutputDirectory')
    parser.add_argument('--native-directory', type=Path, required=True)
    args = parser.parse_args()
    classpath = next(prop.attrib['value'] for prop in ET.parse(args.backend_report).findall('properties/property')
                     if prop.attrib.get('name') == 'java.class.path')
    source = Path(__file__).with_name('QuicInteropServer.java')
    with tempfile.TemporaryDirectory(prefix='chatlite-quic-interop-') as temporary:
        work = Path(temporary)
        cert = work / 'cert.pem'; key = work / 'key.pem'; unknown = work / 'unknown.pem'
        for output, private in [(cert, key), (unknown, work / 'unknown-key.pem')]:
            subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                            '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost',
                            '-keyout', str(private), '-out', str(output)], check=True, capture_output=True)
        subprocess.run(['javac', '--enable-preview', '--release', '25', '-cp', classpath, '-d', str(work), str(source)], check=True)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as reservation:
            reservation.bind(('127.0.0.1', 0))
            port = reservation.getsockname()[1]
        ready = work / 'ready'
        log_path = work / 'server.log'
        with log_path.open('w') as log:
            server = subprocess.Popen(['java', '--enable-preview', '-XX:ActiveProcessorCount=2', '-cp', f'{work}:{classpath}',
                                       'QuicInteropServer', str(port), str(cert), str(key), str(ready)], stdout=log, stderr=subprocess.STDOUT)
            try:
                for _ in range(200):
                    if ready.exists():
                        break
                    if server.poll() is not None:
                        raise RuntimeError('Netty fixture exited: ' + log_path.read_text()[-8000:])
                    time.sleep(.1)
                if not ready.exists():
                    raise RuntimeError('Netty fixture startup timeout: ' + log_path.read_text()[-8000:])
                native = args.native_directory.resolve()
                subprocess.run(['java', '--enable-native-access=ALL-UNNAMED', f'-Djava.library.path={native}', '-cp',
                                f'{args.probe_bundle.resolve()}/classes:{args.probe_bundle.resolve()}/*',
                                'QuicJniProbeKt', 'localhost', str(port), str(cert)], check=True, timeout=45)
                for host, roots, label in [('localhost', cert, 'trusted hostname'),
                                            ('127.0.0.1', cert, 'wrong hostname'),
                                            ('localhost', unknown, 'untrusted certificate')]:
                    result = subprocess.run([str(native / 'quic-probe'), host, str(port), str(roots)], capture_output=True, timeout=20)
                    expected = label == 'trusted hostname'
                    if (result.returncode == 0) != expected:
                        raise RuntimeError(f'TLS verification failed: {label}; {result.stderr.decode(errors="replace")}')
                    print(f'PASS: TLS {label} {"accepted" if expected else "rejected"}', flush=True)
            except Exception:
                print('Netty fixture diagnostics (synthetic traffic only):\n' + log_path.read_text()[-12000:], flush=True)
                raise
            finally:
                server.terminate()
                try:
                    server.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    server.kill(); server.wait()


if __name__ == '__main__':
    main()
