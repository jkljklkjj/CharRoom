use std::collections::{HashMap, VecDeque};
use std::io;
use std::net::{ToSocketAddrs, UdpSocket};
use std::os::fd::AsRawFd;
use std::time::{Duration, Instant};

const MAX_QUEUED_BYTES: usize = 4 * 1024 * 1024;
const MAX_EVENT_BYTES: usize = 16 * 1024 * 1024;
const DATAGRAM_SIZE: usize = 1350;

#[derive(Debug)]
pub enum Event {
    Connected,
    Data(u64, Vec<u8>),
    StreamClosed(u64),
    Closed(String),
}

impl Event {
    // A deliberately small JNI wire format: tag, big-endian stream id, bytes.
    pub fn encode(self) -> Vec<u8> {
        match self {
            Self::Connected => vec![1],
            Self::Data(id, data) => {
                let mut result = vec![2];
                result.extend_from_slice(&id.to_be_bytes());
                result.extend(data);
                result
            }
            Self::StreamClosed(id) => {
                let mut result = vec![3];
                result.extend_from_slice(&id.to_be_bytes());
                result
            }
            Self::Closed(reason) => {
                let mut result = vec![4];
                result.extend(reason.as_bytes());
                result
            }
        }
    }
}

struct PendingWrite {
    bytes: Vec<u8>,
    offset: usize,
}

pub struct Driver {
    socket: UdpSocket,
    conn: quiche::Connection,
    alpn: Vec<u8>,
    established: bool,
    writes: HashMap<u64, VecDeque<PendingWrite>>,
    queued_bytes: usize,
    datagrams: VecDeque<(Vec<u8>, quiche::SendInfo)>,
    events: VecDeque<Event>,
    event_bytes: usize,
}

fn error(message: impl Into<String>) -> io::Error {
    io::Error::other(message.into())
}

impl Driver {
    pub fn connect(host: &str, port: u16, ca_file: &str, alpn: &str) -> io::Result<Self> {
        if host.is_empty() || alpn.is_empty() || ca_file.is_empty() {
            return Err(error("host, trust roots and ALPN are required"));
        }
        let addresses: Vec<_> = (host, port).to_socket_addrs()?.collect();
        let peer = addresses
            .iter()
            .find(|a| a.is_ipv4())
            .or_else(|| addresses.first())
            .copied()
            .ok_or_else(|| error("No address for QUIC host"))?;
        let socket = UdpSocket::bind(if peer.is_ipv4() {
            "0.0.0.0:0"
        } else {
            "[::]:0"
        })?;
        socket.connect(peer)?;
        socket.set_nonblocking(true)?;
        let local = socket.local_addr()?;
        let mut config =
            quiche::Config::new(quiche::PROTOCOL_VERSION).map_err(|e| error(e.to_string()))?;
        config.verify_peer(true);
        config
            .load_verify_locations_from_file(ca_file)
            .map_err(|e| error(format!("Cannot load QUIC trust roots: {e}")))?;
        config
            .set_application_protos(&[alpn.as_bytes()])
            .map_err(|e| error(e.to_string()))?;
        config.set_max_idle_timeout(60_000);
        config.set_max_recv_udp_payload_size(65_527);
        config.set_max_send_udp_payload_size(DATAGRAM_SIZE);
        config.set_initial_max_data(16_777_216);
        config.set_initial_max_stream_data_bidi_local(4_194_304);
        config.set_initial_max_stream_data_bidi_remote(4_194_304);
        config.set_initial_max_stream_data_uni(1_048_576);
        config.set_initial_max_streams_bidi(256);
        config.set_initial_max_streams_uni(128);
        // 0-RTT is intentionally disabled: chat acceptance depends on authenticated business ACKs.
        let mut scid = [0; quiche::MAX_CONN_ID_LEN];
        getrandom::getrandom(&mut scid)
            .map_err(|_| error("Cannot generate QUIC connection identity"))?;
        let id = quiche::ConnectionId::from_ref(&scid);
        let conn = quiche::connect(Some(host), &id, local, peer, &mut config)
            .map_err(|e| error(e.to_string()))?;
        Ok(Self {
            socket,
            conn,
            alpn: alpn.as_bytes().to_vec(),
            established: false,
            writes: HashMap::new(),
            queued_bytes: 0,
            datagrams: VecDeque::new(),
            events: VecDeque::new(),
            event_bytes: 0,
        })
    }

    pub fn send(&mut self, stream: u64, bytes: Vec<u8>) -> io::Result<()> {
        if !self.conn.is_established() || self.conn.is_closed() {
            return Err(error("QUIC is not established"));
        }
        if stream >= (1 << 62) || stream & 3 != 0 {
            return Err(error("Expected a client-initiated bidirectional stream"));
        }
        if bytes.is_empty() || bytes.len() > MAX_QUEUED_BYTES.saturating_sub(self.queued_bytes) {
            return Err(error("QUIC send queue is full"));
        }
        self.queued_bytes += bytes.len();
        self.writes
            .entry(stream)
            .or_default()
            .push_back(PendingWrite { bytes, offset: 0 });
        self.flush_writes()?;
        self.flush_packets()
    }

    fn flush_writes(&mut self) -> io::Result<()> {
        for (id, queue) in self.writes.iter_mut() {
            while let Some(write) = queue.front_mut() {
                match self
                    .conn
                    .stream_send(*id, &write.bytes[write.offset..], false)
                {
                    Ok(0) | Err(quiche::Error::Done) | Err(quiche::Error::StreamLimit) => break,
                    Ok(count) => {
                        write.offset += count;
                        self.queued_bytes -= count;
                        if write.offset == write.bytes.len() {
                            queue.pop_front();
                        }
                    }
                    Err(e) => return Err(error(format!("QUIC stream write failed: {e}"))),
                }
            }
        }
        self.writes.retain(|_, queue| !queue.is_empty());
        Ok(())
    }

    fn flush_packets(&mut self) -> io::Result<()> {
        let mut buffer = [0; DATAGRAM_SIZE];
        // Bound temporary datagrams as well as application writes.
        while self.datagrams.len() < 64 {
            match self.conn.send(&mut buffer) {
                Ok((count, info)) => self.datagrams.push_back((buffer[..count].to_vec(), info)),
                Err(quiche::Error::Done) => break,
                Err(e) => return Err(error(format!("QUIC packet generation failed: {e}"))),
            }
        }
        while let Some((bytes, info)) = self.datagrams.front() {
            if info.at > Instant::now() {
                break;
            }
            match self.socket.send_to(bytes, info.to) {
                Ok(count) if count == bytes.len() => {
                    self.datagrams.pop_front();
                }
                Ok(_) => return Err(error("Truncated UDP send")),
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => break,
                Err(e) => return Err(e),
            }
        }
        Ok(())
    }

    fn receive_packets(&mut self) -> io::Result<()> {
        let mut buffer = [0; 65_535];
        let local = self.socket.local_addr()?;
        for _ in 0..64 {
            match self.socket.recv_from(&mut buffer) {
                Ok((count, from)) => {
                    match self
                        .conn
                        .recv(&mut buffer[..count], quiche::RecvInfo { from, to: local })
                    {
                        Ok(_) | Err(quiche::Error::Done) | Err(quiche::Error::InvalidPacket) => {}
                        Err(e) => return Err(error(format!("QUIC receive/handshake failed: {e}"))),
                    }
                }
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => break,
                Err(e) => return Err(e),
            }
        }
        Ok(())
    }

    fn collect_events(&mut self) -> io::Result<()> {
        if self.conn.is_established() && !self.established {
            if self.conn.application_proto() != self.alpn {
                return Err(error("QUIC ALPN does not match"));
            }
            self.established = true;
            self.events.push_back(Event::Connected);
        }
        let readable: Vec<_> = self.conn.readable().collect();
        let mut buffer = [0; 16_384];
        for id in readable {
            loop {
                match self.conn.stream_recv(id, &mut buffer) {
                    Ok((count, fin)) => {
                        if count != 0 {
                            self.event_bytes += count;
                            if self.event_bytes > MAX_EVENT_BYTES {
                                return Err(error("QUIC receive queue is full"));
                            }
                            self.events
                                .push_back(Event::Data(id, buffer[..count].to_vec()));
                        }
                        if fin {
                            self.events.push_back(Event::StreamClosed(id));
                            break;
                        }
                        if count == 0 {
                            break;
                        }
                    }
                    Err(quiche::Error::Done) => break,
                    Err(quiche::Error::StreamReset(_)) => {
                        self.events.push_back(Event::StreamClosed(id));
                        break;
                    }
                    Err(e) => return Err(error(format!("QUIC stream receive failed: {e}"))),
                }
            }
        }
        if self.conn.is_closed() || self.conn.is_draining() {
            let code = self
                .conn
                .peer_error()
                .or_else(|| self.conn.local_error())
                .map(|e| e.error_code);
            self.events.push_back(Event::Closed(format!(
                "QUIC connection closed (code={code:?})"
            )));
        }
        Ok(())
    }

    pub fn poll(&mut self, timeout: Duration) -> io::Result<Option<Event>> {
        let deadline = Instant::now() + timeout.min(Duration::from_millis(50));
        loop {
            if self.conn.timeout() == Some(Duration::ZERO) {
                self.conn.on_timeout();
            }
            self.receive_packets()?;
            self.flush_writes()?;
            self.flush_packets()?;
            self.collect_events()?;
            if let Some(event) = self.events.pop_front() {
                if let Event::Data(_, ref bytes) = event {
                    self.event_bytes -= bytes.len();
                }
                return Ok(Some(event));
            }
            let remaining = deadline.saturating_duration_since(Instant::now());
            if remaining.is_zero() {
                return Ok(None);
            }
            let mut wait = self.conn.timeout().unwrap_or(remaining).min(remaining);
            if let Some((_, info)) = self.datagrams.front() {
                wait = wait.min(info.at.saturating_duration_since(Instant::now()));
            }
            // Rounded up to avoid a sub-millisecond busy loop, bounded by the caller's deadline.
            let millis = wait.as_millis().saturating_add(1).min(50) as i32;
            let mut fd = libc::pollfd {
                fd: self.socket.as_raw_fd(),
                events: libc::POLLIN,
                revents: 0,
            };
            let result = unsafe { libc::poll(&mut fd, 1, millis) };
            if result < 0 {
                let error = io::Error::last_os_error();
                if error.kind() != io::ErrorKind::Interrupted {
                    return Err(error);
                }
            }
        }
    }

    pub fn close(&mut self) {
        let _ = self.conn.close(true, 0, b"client closing");
        let _ = self.flush_packets();
        self.events.clear();
        self.writes.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn jni_event_format_preserves_stream_identity_and_bytes() {
        let event = Event::Data(4, vec![0, 255, 1]).encode();
        assert_eq!(&event[..9], &[2, 0, 0, 0, 0, 0, 0, 0, 4]);
        assert_eq!(&event[9..], &[0, 255, 1]);
    }
    #[test]
    fn empty_trust_configuration_is_rejected() {
        assert!(Driver::connect("localhost", 443, "", "custom").is_err());
    }
}
