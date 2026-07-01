package com.sw.ck.bpm.process.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点类型规格 —— 定义一种节点类型的入度/出度基数约束与系统属性。
 * <p>
 * 校验器通过 {@link NodeTypeRegistry} 的 Map 查找对应规格，
 * 不使用 switch/if-else 链判类型。新增节点类型 = 加一条注册，不改校验器。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeTypeSpec {

    /** 最小入度。 */
    private int minIn;

    /** 最大入度（{@link Integer#MAX_VALUE} 表示不限制上限）。 */
    private int maxIn;

    /** 最小出度。 */
    private int minOut;

    /** 最大出度（{@link Integer#MAX_VALUE} 表示不限制上限）。 */
    private int maxOut;

    /** 是否系统管理节点（START/END 不可删）。 */
    private boolean systemManaged;

    /** 是否允许用户删除。 */
    private boolean deletable;
}
