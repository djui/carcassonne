Carcassonne
===========

A server implementation enabling playing games between clients.

## Server

The server accepts TCP connections at port *31183* with `\n` newline separated
frames. Each frame must be a valid UTF-8 encoded byte string composing a
message. Each message must be a valid JSON data structure. Messages are composed
to construct a game and turn protocol.

## Game and Turn Protocol

### Game messages


### Turn messages

