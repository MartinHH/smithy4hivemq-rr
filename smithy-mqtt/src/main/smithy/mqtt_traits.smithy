$version: "2"

namespace io.github.martinhh.mqtt

@documentation("An operation that shall be mapped to mqtt 5 requess/response pattern")
@trait(selector: "operation")
structure mqttRequest {
    @required
    topic: String
}
