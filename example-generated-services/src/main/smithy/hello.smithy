$version: "2"

namespace hello

use alloy#simpleRestJson
use io.github.martinhh.mqtt#mqttRequest

@documentation("An example service: returns a greeting message")
@simpleRestJson
service HelloWorldService {
  version: "1.0.0",
  operations: [Hello]
}

@documentation("Returns a greeting message to the given person")
@http(method: "GET", uri: "/hello/{name}", code: 200)
@readonly
@mqttRequest(topic: "hello")
operation Hello {
  input: Person,
  output: Greeting
}

@documentation("A person")
structure Person {
  @documentation("The Person's name")
  @httpLabel
  @required
  name: String,

  @documentation("The town where the person is based")
  @httpQuery("town")
  town: String
}

@documentation("A personalized greeting message")
structure Greeting {
  @documentation("The actual message")
  @required
  message: String
}
