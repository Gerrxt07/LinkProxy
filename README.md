# LinkProxy

LinkProxy is our Minecraft proxy build. It is based on PaperMC Velocity and is maintained with 
the network-specific reliability and operations changes we need in production.

## What changed in this version

This version improves connection visibility and fixes early-pipeline packet
forwarding around login and proxy protocol handling.

* Login handshakes now log the remote address, protocol version, requested host,
  and resolved connection type.
* Login packets now log the remote address, username, and client protocol version
  before pre-login processing starts.
* Auth sessions now log the player name, online-mode state, and protocol version
  when authentication activates.
* Legacy ping decoding now forwards any unread bytes after the decoder removes
  itself from the Netty pipeline.
* PROXY protocol v2 decoding now forwards remaining bytes for both LOCAL and
  PROXY commands after the proxied address is handled.
* PROXY protocol v2 address parsing now consumes the destination port explicitly,
  keeping the byte reader aligned with the protocol frame.

## Benefits for our system

These changes make LinkProxy easier to operate and safer under mixed client and
upstream traffic patterns.

* Faster troubleshooting: connection, login, and auth logs provide enough context
  to trace a player from handshake through authentication without attaching a
  debugger or adding temporary log patches.
* Better protocol resilience: leftover bytes are preserved when early decoders
  finish, which prevents valid client data from being stranded after legacy ping
  or PROXY protocol detection.
* Cleaner load balancer support: PROXY protocol v2 LOCAL and PROXY commands now
  exit the decoder consistently while passing subsequent Minecraft data onward.
* Lower operational risk: the additional logging focuses on connection metadata
  that helps diagnose protocol/version/routing issues while avoiding invasive
  packet dumps.

## Building

LinkProxy is built with Gradle. Use the wrapper included in this repository:

```bash
./gradlew build
```

The runnable proxy artifact is produced under `proxy/build/libs`.

## Running

After building, start the proxy with the generated all-in-one JAR from
`proxy/build/libs`, or use the local helper script when running from this checkout:

```bash
./run-proxy.sh
```

Runtime configuration is generated on first start and should be adjusted for the
current network environment before production use.

## License

LinkProxy inherits the GPLv3 license from Velocity.
