$version: "2"

namespace hello.mqtt

@documentation("An operation that shall be mapped to mqtt 5 requess/response pattern")
@trait(selector: "operation")
structure mqttRequest {
    @required
    topic: String
}
