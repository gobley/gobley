/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use boring::ssl::SslConnector;
use boring::ssl::SslMethod;
use boring::ssl::SslVerifyMode;
use tokio::io::AsyncReadExt;
use tokio::io::AsyncWriteExt;
use tokio::net::TcpStream;
use url::Url;

#[uniffi::export(async_runtime = "tokio")]
async fn retrieve_from(url: String) -> String {
    let url = Url::parse(&url).expect("invalid url");
    let host = url.host_str().expect("host missing");
    let port = url
        .port()
        .or_else(|| {
            Some(match url.scheme() {
                "http" => 80,
                "https" => 443,
                _ => return None,
            })
        })
        .expect("cannot determine the port number");

    let stream = TcpStream::connect(format!("{host}:{port}"))
        .await
        .expect("failed to open a TCP connection");

    let ssl = SslMethod::tls_client();
    let mut ssl_connector = SslConnector::builder(ssl).expect("failed to create a SslConnector");
    ssl_connector.set_verify(SslVerifyMode::NONE);

    let mut stream =
        tokio_boring::connect(ssl_connector.build().configure().unwrap(), host, stream)
            .await
            .unwrap();

    stream
        .write_all(
            format!("GET / HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n",).as_bytes(),
        )
        .await
        .expect("failed to send request");

    let mut result = String::new();
    stream.read_to_string(&mut result).await.unwrap();
    result
}

uniffi::setup_scaffolding!();
