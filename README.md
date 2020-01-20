# Akkounts #

Small demo project for Akka, its use case is bank accounts: deposit, withdraw and get the balance.
Akkounts is a distributed system making use of Akka Cluster. It employs Event Sourcing and CQRS
enabled by Akka Persistence and Akka Persistence Query and offers a HTTP/JSON API through Akka HTTP.
Domain logic processing is using Akka Streams and [Streamee](https://github.com/moia-dev/streamee)
processors.

## Run locally

Make sure to run Cassandra or Scylla locally using the default ports. You can use the provided
`docker-compose.yml` and simply run `docker-compose up`.

Then run `sbt r1` (r1 is defined as a command alias in `build.sbt`) to start a node with all
relevant services bound to 127.0.0.1. You can also run `sbt r2`, but first you have to add
127.0.0.2 as a network interface, e.g. via `sudo ifconfig lo0 alias 127.0.0.2` on macOS.

## Usage Example

Assuming you have [HTTPie](https://httpie.org) installed, you can call the HTTP API like this:

```
~/code/scala/rocks.heikoseeberger/akkounts(master ✗) http 127.0.0.1:8080/42/deposit amount:=10
HTTP/1.1 201 Created
Content-Length: 21
Content-Type: application/json
Date: Wed, 05 Feb 2020 17:00:51 GMT
Server: akka-http/10.1.11

"Deposited amount 10"

~/code/scala/rocks.heikoseeberger/akkounts(master ✗) http 127.0.0.1:8080/42/withdraw amount:=7
HTTP/1.1 201 Created
Content-Length: 20
Content-Type: application/json
Date: Wed, 05 Feb 2020 17:00:55 GMT
Server: akka-http/10.1.11

"Withdrawn amount 7"

~/code/scala/rocks.heikoseeberger/akkounts(master ✗) http 127.0.0.1:8080/42/withdraw amount:=5
HTTP/1.1 400 Bad Request
Content-Length: 38
Content-Type: application/json
Date: Wed, 05 Feb 2020 17:00:56 GMT
Server: akka-http/10.1.11

"Insufficient balance 3 for amount 5!"

~/code/scala/rocks.heikoseeberger/akkounts(master ✗) http 127.0.0.1:8080/42
HTTP/1.1 200 OK
Content-Length: 14
Content-Type: application/json
Date: Wed, 05 Feb 2020 17:01:05 GMT
Server: akka-http/10.1.11

"Balance is 3"
```   

## Run in k8s

TODO 

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with
any pull requests, please state that the contribution is your original work and that you license
the work to the project under the project's open source license. Whether or not you state this
explicitly, by submitting any copyrighted material via pull request, email, or other means you
agree to license the material under the project's open source license and warrant that you have the
legal authority to do so.

## License ##

This code is open source software licensed under the
[Apache-2.0](http://www.apache.org/licenses/LICENSE-2.0) license.
