# DualConduit Project

DualConduit is a service that converts restricted unidirectional connections
into full-duplex communication pathways.

## Usage

This guide explains how to use DualConduit to establish communication pathways.

### Prerequisites

-   Docker installed.
-   Bazel installed.

### Starting the Acceptor

The Acceptor listens for incoming connections from the Dialer and manages the
mesh network (xDS server).

1.  Build and load the Acceptor Docker image: `bash bazel run
    //src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/acceptor:acceptor_load`

2.  Create a Docker network to allow containers to communicate: `bash docker
    network create dualconduit-net`

3.  Run the Acceptor container: `bash docker run -d --name acceptor --network
    dualconduit-net -p 7878:7878 -p 18000:18000 dualconduit/acceptor:latest`

    *   Port `7878` is the RSocket server port.
    *   Port `18000` is the xDS gRPC server port.

### Starting the Dialer

The Dialer connects to the Acceptor and can establish conduits.

1.  Build and load the Dialer Docker image: `bash bazel run
    //src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/dialer:dialer_load`

2.  Run the Dialer container on the same network: `bash docker run -d --name
    dialer --network dualconduit-net -p 50051:50051 dualconduit/dialer:latest
    --acceptor_target=acceptor:7878`

    *   Port `50051` is the port for the Dialer gRPC service.
    *   `--acceptor_target` points to the Acceptor container (`acceptor`) on
        port `7878`.

### Establishing a Conduit

Once both Acceptor and Dialer are running, you can establish a conduit in two
ways:

#### 1. Via Command Line Flag (`-L`) on Dialer Startup

You can specify conduits to be established immediately when starting the dialer
using the `-L` flag.

```bash
docker run -d --name dialer --network dualconduit-net dualconduit/dialer:latest --acceptor_target=acceptor:7878 -L entry_port:destination_endpoint
```

*   `entry_port`: The port on the Acceptor side that will be forwarded.
*   `destination_endpoint`: The target endpoint reachable from the Dialer side.

Example: `bash -L 8080:localhost:80` This will forward traffic arriving at port
`8080` on the Acceptor side to `localhost:80` on the Dialer side.

#### 2. Via gRPC

The Dialer exposes a gRPC service (`DualConduitService`) that allows you to
manage conduits dynamically.

You can use a gRPC client to call `EstablishConduit` on the Dialer (listening on
port `50051` in the example above).

Service definition can be found in
`src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dual_conduit_service.proto`.

## Reverse Conduits and Control Plane

Reverse conduits allow the Acceptor side to initiate connections to services
exposed by the Dialer side.

> [!IMPORTANT] Reverse conduits can **only** be established by calling the
> `EstablishConduit` gRPC method on the Dialer. They cannot be established via
> the `-L` command-line flag.

When you successfully establish a reverse conduit via gRPC, the
`EstablishConduitResponse` contains a `ServiceLocator`. This locator provides
the address or routing information needed to connect to the service through the
control plane.

### gRPC

For gRPC services, the `ServiceLocator` will contain an `xds_address`. You can
use this address with the `xds:///` resolver in your gRPC client.

Example target: `xds:///my-service.dcon` (obtained from `xds_address`)

### HTTP and TCP

For HTTP and TCP traffic, you must start an Envoy proxy that receives routing
configuration from the Acceptor's xDS server.

#### HTTP

For HTTP services, the `ServiceLocator` will contain a `virtual_host`. You must
use this value as the `Host` header when sending requests to the Envoy proxy.

Example:
```bash
# Use the virtual_host value as the Host header
curl -H "Host: VIRTUAL_HOST_VALUE" http://envoy-proxy:port/path
```

#### TCP

For raw TCP traffic, the `ServiceLocator` will contain an `sni` value. Envoy
routes the traffic based on this SNI. You can use a tool like `socat` to add the
SNI to a TCP connection before sending it to the Envoy proxy.

Example using `socat`:
```bash
socat TCP-LISTEN:local-port,reuseaddr,fork OPENSSL:envoy-proxy:port,verify=0,sni=SNI_VALUE
```

## Tearing Down a Conduit

To tear down an established conduit, you can use the `TeardownConduit` gRPC
method on the Dialer service.

You need to provide the `conduit_id` which was returned in the
`EstablishConduitResponse` when the conduit was created.

Service definition can be found in
`src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dual_conduit_service.proto`.
