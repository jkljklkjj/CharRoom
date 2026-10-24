use chatlite_quic::driver::{Driver, Event};
use std::time::{Duration, Instant};
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<_> = std::env::args().collect();
    if args.len() != 4 {
        return Err("usage: quic-probe HOST PORT CA_FILE".into());
    }
    let mut driver = Driver::connect(&args[1], args[2].parse()?, &args[3], "custom")?;
    let until = Instant::now() + Duration::from_secs(15);
    while Instant::now() < until {
        match driver.poll(Duration::from_millis(50))? {
            Some(Event::Connected) => {
                println!("PASS: QUIC v1, custom ALPN, verified certificate and hostname");
                driver.close();
                return Ok(());
            }
            Some(Event::Closed(reason)) => return Err(reason.into()),
            _ => {}
        }
    }
    Err("QUIC handshake timed out".into())
}
