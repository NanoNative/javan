package javan.ir;

import java.util.List;

/**
 * Closed-world virtual/interface dispatch stub metadata.
 *
 * @param symbol C dispatch function symbol
 * @param returnType return type
 * @param parameters dispatch parameters, including receiver first
 * @param targets concrete receiver targets
 */
public record IrDispatch(String symbol, IrType returnType, List<IrParameter> parameters, List<IrDispatchTarget> targets) {
}
