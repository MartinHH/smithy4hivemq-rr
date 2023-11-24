$version: "2"

namespace hello

use alloy#simpleRestJson

@documentation("Another example service: providing a mutable \"counter\" state")
@simpleRestJson
service CountService {
    version: "1.0.0"
    operations: [Modify, GetCount]
}

@documentation("Returns the current value of the counter")
@http(method: "GET", uri: "/value", code: 200)
@readonly
operation GetCount {
    output: CurrentCount
}

@documentation("Modifies the current value of the counter")
@http(method: "POST", uri: "/value", code: 200)
operation Modify {
    input: ValueMod
    errors: [DivisionByZero]
}

@error("client")
structure DivisionByZero {
    @required
    message: String
}

enum MathOp {
    ADD = "add"
    SUB = "sub"
    MUL = "mul"
    DIV = "div"
}


@documentation("Parameters for a modification of the current value of the counter")
structure ValueMod {
    @documentation("The operator of the operation that shall be applied to the value")
    @required
    operator: MathOp

    @documentation("The (right-hand) operand of the operation that shall be applied to the value")
    @required
    operand: Integer
}

structure CurrentCount {
    @required
    value: Integer
}
