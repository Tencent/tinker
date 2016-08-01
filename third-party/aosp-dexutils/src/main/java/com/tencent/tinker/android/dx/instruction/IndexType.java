/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.android.dx.instruction;

import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dx.util.Hex;

/**
 * The various types that an index in a Dalvik instruction might refer to.
 */
public enum IndexType {
    /** "Unknown." Used for undefined opcodes. */
    UNKNOWN,

    /** no index used */
    NONE,

    /** "It depends." Used for {@code throw-verification-error}. */
    VARIES,

    /** type reference index */
    TYPE_REF,

    /** string reference index */
    STRING_REF,

    /** method reference index */
    METHOD_REF,

    /** field reference index */
    FIELD_REF,

    /** inline method index (for inline linked method invocations) */
    INLINE_METHOD,

    /** direct vtable offset (for static linked method invocations) */
    VTABLE_OFFSET,

    /** direct field offset (for static linked field accesses) */
    FIELD_OFFSET;

    public static IndexType getIndexType(int opcode) {
        switch (opcode) {
            case Opcodes.CONST_STRING:
            case Opcodes.CONST_STRING_JUMBO: {
                return IndexType.STRING_REF;
            }
            case Opcodes.CONST_CLASS:
            case Opcodes.CHECK_CAST:
            case Opcodes.INSTANCE_OF:
            case Opcodes.NEW_INSTANCE:
            case Opcodes.NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY_RANGE: {
                return IndexType.TYPE_REF;
            }
            case Opcodes.IGET:
            case Opcodes.IGET_WIDE:
            case Opcodes.IGET_OBJECT:
            case Opcodes.IGET_BOOLEAN:
            case Opcodes.IGET_BYTE:
            case Opcodes.IGET_CHAR:
            case Opcodes.IGET_SHORT:
            case Opcodes.IPUT:
            case Opcodes.IPUT_WIDE:
            case Opcodes.IPUT_OBJECT:
            case Opcodes.IPUT_BOOLEAN:
            case Opcodes.IPUT_BYTE:
            case Opcodes.IPUT_CHAR:
            case Opcodes.IPUT_SHORT:
            case Opcodes.SGET:
            case Opcodes.SGET_WIDE:
            case Opcodes.SGET_OBJECT:
            case Opcodes.SGET_BOOLEAN:
            case Opcodes.SGET_BYTE:
            case Opcodes.SGET_CHAR:
            case Opcodes.SGET_SHORT:
            case Opcodes.SPUT:
            case Opcodes.SPUT_WIDE:
            case Opcodes.SPUT_OBJECT:
            case Opcodes.SPUT_BOOLEAN:
            case Opcodes.SPUT_BYTE:
            case Opcodes.SPUT_CHAR:
            case Opcodes.SPUT_SHORT: {
                return IndexType.FIELD_REF;
            }
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE:
            case Opcodes.INVOKE_VIRTUAL_RANGE:
            case Opcodes.INVOKE_SUPER_RANGE:
            case Opcodes.INVOKE_DIRECT_RANGE:
            case Opcodes.INVOKE_STATIC_RANGE:
            case Opcodes.INVOKE_INTERFACE_RANGE: {
                return IndexType.METHOD_REF;
            }
            case Opcodes.SPECIAL_FORMAT:
            case Opcodes.PACKED_SWITCH_PAYLOAD:
            case Opcodes.SPARSE_SWITCH_PAYLOAD:
            case Opcodes.FILL_ARRAY_DATA_PAYLOAD:
            case Opcodes.NOP:
            case Opcodes.MOVE:
            case Opcodes.MOVE_FROM16:
            case Opcodes.MOVE_16:
            case Opcodes.MOVE_WIDE:
            case Opcodes.MOVE_WIDE_FROM16:
            case Opcodes.MOVE_WIDE_16:
            case Opcodes.MOVE_OBJECT:
            case Opcodes.MOVE_OBJECT_FROM16:
            case Opcodes.MOVE_OBJECT_16:
            case Opcodes.MOVE_RESULT:
            case Opcodes.MOVE_RESULT_WIDE:
            case Opcodes.MOVE_RESULT_OBJECT:
            case Opcodes.MOVE_EXCEPTION:
            case Opcodes.RETURN_VOID:
            case Opcodes.RETURN:
            case Opcodes.RETURN_WIDE:
            case Opcodes.RETURN_OBJECT:
            case Opcodes.CONST_4:
            case Opcodes.CONST_16:
            case Opcodes.CONST:
            case Opcodes.CONST_HIGH16:
            case Opcodes.CONST_WIDE_16:
            case Opcodes.CONST_WIDE_32:
            case Opcodes.CONST_WIDE:
            case Opcodes.CONST_WIDE_HIGH16:
            case Opcodes.MONITOR_ENTER:
            case Opcodes.MONITOR_EXIT:
            case Opcodes.ARRAY_LENGTH:
            case Opcodes.FILL_ARRAY_DATA:
            case Opcodes.THROW:
            case Opcodes.GOTO:
            case Opcodes.GOTO_16:
            case Opcodes.GOTO_32:
            case Opcodes.PACKED_SWITCH:
            case Opcodes.SPARSE_SWITCH:
            case Opcodes.CMPL_FLOAT:
            case Opcodes.CMPG_FLOAT:
            case Opcodes.CMPL_DOUBLE:
            case Opcodes.CMPG_DOUBLE:
            case Opcodes.CMP_LONG:
            case Opcodes.IF_EQ:
            case Opcodes.IF_NE:
            case Opcodes.IF_LT:
            case Opcodes.IF_GE:
            case Opcodes.IF_GT:
            case Opcodes.IF_LE:
            case Opcodes.IF_EQZ:
            case Opcodes.IF_NEZ:
            case Opcodes.IF_LTZ:
            case Opcodes.IF_GEZ:
            case Opcodes.IF_GTZ:
            case Opcodes.IF_LEZ:
            case Opcodes.AGET:
            case Opcodes.AGET_WIDE:
            case Opcodes.AGET_OBJECT:
            case Opcodes.AGET_BOOLEAN:
            case Opcodes.AGET_BYTE:
            case Opcodes.AGET_CHAR:
            case Opcodes.AGET_SHORT:
            case Opcodes.APUT:
            case Opcodes.APUT_WIDE:
            case Opcodes.APUT_OBJECT:
            case Opcodes.APUT_BOOLEAN:
            case Opcodes.APUT_BYTE:
            case Opcodes.APUT_CHAR:
            case Opcodes.APUT_SHORT:
            case Opcodes.NEG_INT:
            case Opcodes.NOT_INT:
            case Opcodes.NEG_LONG:
            case Opcodes.NOT_LONG:
            case Opcodes.NEG_FLOAT:
            case Opcodes.NEG_DOUBLE:
            case Opcodes.INT_TO_LONG:
            case Opcodes.INT_TO_FLOAT:
            case Opcodes.INT_TO_DOUBLE:
            case Opcodes.LONG_TO_INT:
            case Opcodes.LONG_TO_FLOAT:
            case Opcodes.LONG_TO_DOUBLE:
            case Opcodes.FLOAT_TO_INT:
            case Opcodes.FLOAT_TO_LONG:
            case Opcodes.FLOAT_TO_DOUBLE:
            case Opcodes.DOUBLE_TO_INT:
            case Opcodes.DOUBLE_TO_LONG:
            case Opcodes.DOUBLE_TO_FLOAT:
            case Opcodes.INT_TO_BYTE:
            case Opcodes.INT_TO_CHAR:
            case Opcodes.INT_TO_SHORT:
            case Opcodes.ADD_INT:
            case Opcodes.SUB_INT:
            case Opcodes.MUL_INT:
            case Opcodes.DIV_INT:
            case Opcodes.REM_INT:
            case Opcodes.AND_INT:
            case Opcodes.OR_INT:
            case Opcodes.XOR_INT:
            case Opcodes.SHL_INT:
            case Opcodes.SHR_INT:
            case Opcodes.USHR_INT:
            case Opcodes.ADD_LONG:
            case Opcodes.SUB_LONG:
            case Opcodes.MUL_LONG:
            case Opcodes.DIV_LONG:
            case Opcodes.REM_LONG:
            case Opcodes.AND_LONG:
            case Opcodes.OR_LONG:
            case Opcodes.XOR_LONG:
            case Opcodes.SHL_LONG:
            case Opcodes.SHR_LONG:
            case Opcodes.USHR_LONG:
            case Opcodes.ADD_FLOAT:
            case Opcodes.SUB_FLOAT:
            case Opcodes.MUL_FLOAT:
            case Opcodes.DIV_FLOAT:
            case Opcodes.REM_FLOAT:
            case Opcodes.ADD_DOUBLE:
            case Opcodes.SUB_DOUBLE:
            case Opcodes.MUL_DOUBLE:
            case Opcodes.DIV_DOUBLE:
            case Opcodes.REM_DOUBLE:
            case Opcodes.ADD_INT_2ADDR:
            case Opcodes.SUB_INT_2ADDR:
            case Opcodes.MUL_INT_2ADDR:
            case Opcodes.DIV_INT_2ADDR:
            case Opcodes.REM_INT_2ADDR:
            case Opcodes.AND_INT_2ADDR:
            case Opcodes.OR_INT_2ADDR:
            case Opcodes.XOR_INT_2ADDR:
            case Opcodes.SHL_INT_2ADDR:
            case Opcodes.SHR_INT_2ADDR:
            case Opcodes.USHR_INT_2ADDR:
            case Opcodes.ADD_LONG_2ADDR:
            case Opcodes.SUB_LONG_2ADDR:
            case Opcodes.MUL_LONG_2ADDR:
            case Opcodes.DIV_LONG_2ADDR:
            case Opcodes.REM_LONG_2ADDR:
            case Opcodes.AND_LONG_2ADDR:
            case Opcodes.OR_LONG_2ADDR:
            case Opcodes.XOR_LONG_2ADDR:
            case Opcodes.SHL_LONG_2ADDR:
            case Opcodes.SHR_LONG_2ADDR:
            case Opcodes.USHR_LONG_2ADDR:
            case Opcodes.ADD_FLOAT_2ADDR:
            case Opcodes.SUB_FLOAT_2ADDR:
            case Opcodes.MUL_FLOAT_2ADDR:
            case Opcodes.DIV_FLOAT_2ADDR:
            case Opcodes.REM_FLOAT_2ADDR:
            case Opcodes.ADD_DOUBLE_2ADDR:
            case Opcodes.SUB_DOUBLE_2ADDR:
            case Opcodes.MUL_DOUBLE_2ADDR:
            case Opcodes.DIV_DOUBLE_2ADDR:
            case Opcodes.REM_DOUBLE_2ADDR:
            case Opcodes.ADD_INT_LIT16:
            case Opcodes.RSUB_INT:
            case Opcodes.MUL_INT_LIT16:
            case Opcodes.DIV_INT_LIT16:
            case Opcodes.REM_INT_LIT16:
            case Opcodes.AND_INT_LIT16:
            case Opcodes.OR_INT_LIT16:
            case Opcodes.XOR_INT_LIT16:
            case Opcodes.ADD_INT_LIT8:
            case Opcodes.RSUB_INT_LIT8:
            case Opcodes.MUL_INT_LIT8:
            case Opcodes.DIV_INT_LIT8:
            case Opcodes.REM_INT_LIT8:
            case Opcodes.AND_INT_LIT8:
            case Opcodes.OR_INT_LIT8:
            case Opcodes.XOR_INT_LIT8:
            case Opcodes.SHL_INT_LIT8:
            case Opcodes.SHR_INT_LIT8:
            case Opcodes.USHR_INT_LIT8: {
                return IndexType.NONE;
            }
            default: {
                throw new DexException("Bad opcode: " + Hex.u2(opcode));
            }
        }
    }
}
