$version: "2"

namespace hello

use alloy#simpleRestJson

@simpleRestJson
service HelloWorldService {
  version: "1.0.0",
  operations: [Hello]
}

@documentation("This is the documentation of the Hello-operation")
@http(method: "GET", uri: "/hello/{name}", code: 200)
operation Hello {
  input: Person,
  output: Greeting
}

@documentation("This is the documentation of the Person-struct")
structure Person {
  @documentation("This is the documentation of the Person's name")
  @httpLabel
  @required
  name: String,

  @documentation("This is the documentation of the Person's town")
  @httpQuery("town")
  town: String
}

@documentation("This is the documentation of the Greeting-struct")
structure Greeting {
  @documentation("This is the documentation of the Greeting's message")
  @required
  message: String
}
