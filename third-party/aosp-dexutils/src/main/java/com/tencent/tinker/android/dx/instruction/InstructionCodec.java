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

import static com.tencent.tinker.android.dx.instruction.Opcodes.extractOpcodeFromUnit;

import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dx.util.Hex;

import java.io.EOFException;
import java.util.Arrays;

/**
 * Encode/Decode instruction opcode.
 */
public final class InstructionCodec {
    /**
     * "Unknown." Used for undefined opcodes.
     */
    public static final int INDEX_TYPE_UNKNOWN = 0;
    /**
     * no index used
     */
    public static final int INDEX_TYPE_NONE = 1;
    /**
     * type reference index
     */
    public static final int INDEX_TYPE_TYPE_REF = 2;
    /**
     * string reference index
     */
    public static final int INDEX_TYPE_STRING_REF = 3;
    /**
     * method reference index
     */
    public static final int INDEX_TYPE_METHOD_REF = 4;
    /**
     * field reference index
     */
    public static final int INDEX_TYPE_FIELD_REF = 5;
    /**
     * method index and a proto index
     */
    public static final int INDEX_TYPE_METHOD_AND_PROTO_REF = 6;
    /**
     * call site reference index
     */
    public static final int INDEX_TYPE_CALL_SITE_REF = 7;
    /**
     * method handle reference index (for loading constant method handles)
     */
    public static final int INDEX_TYPE_METHOD_HANDLE_REF = 8;
    /**
     * proto reference index (for loading constant proto ref)
     */
    public static final int INDEX_TYPE_PROTO_REF = 9;

    /**
     * "Unknown." Used for undefined opcodes.
     */
    public static final int INSN_FORMAT_UNKNOWN = 0;
    public static final int INSN_FORMAT_00X = 1;
    public static final int INSN_FORMAT_10T = 2;
    public static final int INSN_FORMAT_10X = 3;
    public static final int INSN_FORMAT_11N = 4;
    public static final int INSN_FORMAT_11X = 5;
    public static final int INSN_FORMAT_12X = 6;
    public static final int INSN_FORMAT_20T = 7;
    public static final int INSN_FORMAT_21C = 8;
    public static final int INSN_FORMAT_21H = 9;
    public static final int INSN_FORMAT_21S = 10;
    public static final int INSN_FORMAT_21T = 11;
    public static final int INSN_FORMAT_22B = 12;
    public static final int INSN_FORMAT_22C = 13;
    public static final int INSN_FORMAT_22S = 14;
    public static final int INSN_FORMAT_22T = 15;
    public static final int INSN_FORMAT_22X = 16;
    public static final int INSN_FORMAT_23X = 17;
    public static final int INSN_FORMAT_30T = 18;
    public static final int INSN_FORMAT_31C = 19;
    public static final int INSN_FORMAT_31I = 20;
    public static final int INSN_FORMAT_31T = 21;
    public static final int INSN_FORMAT_32X = 22;
    public static final int INSN_FORMAT_35C = 23;
    public static final int INSN_FORMAT_3RC = 24;
    public static final int INSN_FORMAT_51L = 25;
    public static final int INSN_FORMAT_45CC = 26;
    public static final int INSN_FORMAT_4RCC = 27;
    public static final int INSN_FORMAT_PACKED_SWITCH_PAYLOAD = 28;
    public static final int INSN_FORMAT_SPARSE_SWITCH_PAYLOAD = 29;
    public static final int INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD = 30;

    private InstructionCodec() {
        throw new UnsupportedOperationException();
    }

    public static void decode(ShortArrayCodeInput in, InstructionVisitor iv) throws EOFException {
        in.reset();
        while (in.hasMore()) {
            final int currentAddress = in.cursor();
            final int opcodeUnit = in.read();
            final int opcode = extractOpcodeFromUnit(opcodeUnit);
            final int insnFormat = getInstructionFormat(opcode);
            switch (insnFormat) {
                case INSN_FORMAT_00X: {
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, 0L);
                    break;
                }
                case INSN_FORMAT_10X: {
                    final int literal = byte1(opcodeUnit);
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal);
                    break;
                }
                case INSN_FORMAT_12X: {
                    final int a = nibble2(opcodeUnit);
                    final int b = nibble3(opcodeUnit);
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, 0L, a, b);
                    break;
                }
                case INSN_FORMAT_11N: {
                    final int a = nibble2(opcodeUnit);
                    final int literal = (nibble3(opcodeUnit) << 28) >> 28; // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case INSN_FORMAT_11X: {
                    final int a = byte1(opcodeUnit);
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, 0L, a);
                    break;
                }
                case INSN_FORMAT_10T: {
                    final int target = currentAddress +  (byte) byte1(opcodeUnit); // sign-extend
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, target, 0L);
                    break;
                }
                case INSN_FORMAT_20T: {
                    final int literal = byte1(opcodeUnit); // should be zero
                    final int target = currentAddress + (short) in.read(); // sign-extend
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, target, literal);
                    break;
                }
                case INSN_FORMAT_22X: {
                    final int a = byte1(opcodeUnit);
                    final int b = in.read();
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, 0L, a, b);
                    break;
                }
                case INSN_FORMAT_21T: {
                    final int a = byte1(opcodeUnit);
                    final int target = currentAddress + (short) in.read(); // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, target, 0L, a);
                    break;
                }
                case INSN_FORMAT_21S: {
                    final int a = byte1(opcodeUnit);
                    final int literal = (short) in.read(); // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case INSN_FORMAT_21H: {
                    final int a = byte1(opcodeUnit);
                    long literal = (short) in.read(); // sign-extend

                    /*
                     * Format 21h decodes differently depending on the opcode,
                     * because the "signed hat" might represent either a 32-
                     * or 64- bit value.
                     */
                    literal <<= (opcode == Opcodes.CONST_HIGH16) ? 16 : 48;
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case INSN_FORMAT_21C: {
                    final int a = byte1(opcodeUnit);
                    final int index = in.read();
                    final int indexType = getInstructionIndexType(opcode);
                    iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                    break;
                }
                case INSN_FORMAT_23X: {
                    final int a = byte1(opcodeUnit);
                    final int bc = in.read();
                    final int b = byte0(bc);
                    final int c = byte1(bc);
                    iv.visitThreeRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, 0L, a, b, c);
                    break;
                }
                case INSN_FORMAT_22B: {
                    final int a = byte1(opcodeUnit);
                    final int bc = in.read();
                    final int b = byte0(bc);
                    final int literal = (byte) byte1(bc); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a, b);
                    break;
                }
                case INSN_FORMAT_22T: {
                    final int a = nibble2(opcodeUnit);
                    final int b = nibble3(opcodeUnit);
                    final int target = currentAddress + (short) in.read(); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, target, 0L, a, b);
                    break;
                }
                case INSN_FORMAT_22S: {
                    final int a = nibble2(opcodeUnit);
                    final int b = nibble3(opcodeUnit);
                    final int literal = (short) in.read(); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a, b);
                    break;
                }
                case INSN_FORMAT_22C: {
                    final int a = nibble2(opcodeUnit);
                    final int b = nibble3(opcodeUnit);
                    final int index = in.read();
                    final int indexType = getInstructionIndexType(opcode);
                    iv.visitTwoRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b);
                    break;
                }
                case INSN_FORMAT_30T: {
                    final int literal = byte1(opcodeUnit); // should be zero
                    final int target = currentAddress + in.readInt();
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, target, literal);
                    break;
                }
                case INSN_FORMAT_32X: {
                    final int literal = byte1(opcodeUnit); // should be zero
                    final int a = in.read();
                    final int b = in.read();
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a, b);
                    break;
                }
                case INSN_FORMAT_31I: {
                    final int a = byte1(opcodeUnit);
                    final int literal = in.readInt();
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case INSN_FORMAT_31T: {
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int target = currentAddress + in.readInt();
                    /*
                     * Switch instructions need to "forward" their addresses to their
                     * payload target instructions.
                     */
                    switch (opcode) {
                        case Opcodes.PACKED_SWITCH:
                        case Opcodes.SPARSE_SWITCH: {
                            // plus 1 means when we actually lookup the currentAddress
                            // by (payload insn address + 1),
                            in.setBaseAddress(target + 1, currentAddress);
                            break;
                        }
                        default: {
                            break;
                        }
                    }

                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, target, 0L, a);
                    break;
                }
                case INSN_FORMAT_31C: {
                    final int a = byte1(opcodeUnit);
                    final int index = in.readInt();
                    final int indexType = getInstructionIndexType(opcode);
                    iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                    break;
                }
                case INSN_FORMAT_35C: {
                    final int e = nibble2(opcodeUnit);
                    final int registerCount = nibble3(opcodeUnit);
                    final int index = in.read();
                    final int abcd = in.read();
                    final int a = nibble0(abcd);
                    final int b = nibble1(abcd);
                    final int c = nibble2(abcd);
                    final int d = nibble3(abcd);
                    final int indexType = getInstructionIndexType(opcode);

                    switch (registerCount) {
                        case 0:
                            iv.visitZeroRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L);
                            break;
                        case 1:
                            iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                            break;
                        case 2:
                            iv.visitTwoRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b);
                            break;
                        case 3:
                            iv.visitThreeRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c);
                            break;
                        case 4:
                            iv.visitFourRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c, d);
                            break;
                        case 5:
                            iv.visitFiveRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c, d, e);
                            break;
                        default:
                            throw new DexException("bogus registerCount: " + Hex.uNibble(registerCount));
                            // FIXME debug here.
                    }

                    break;
                }
                case INSN_FORMAT_3RC: {
                    final int registerCount = byte1(opcodeUnit);
                    final int index = in.read();
                    final int a = in.read();
                    final int indexType = getInstructionIndexType(opcode);
                    iv.visitRegisterRangeInsn(currentAddress, opcode, index, indexType, 0, 0L, a, registerCount);
                    break;
                }
                case INSN_FORMAT_51L: {
                    final int a = byte1(opcodeUnit);
                    final long literal = in.readLong();
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case INSN_FORMAT_45CC: {
                    if (opcode != Opcodes.INVOKE_POLYMORPHIC) {
                        // 45cc isn't currently used for anything other than invoke-polymorphic.
                        // If that changes, add a more general DecodedInstruction for this format.
                        // TODO keep track on aosp dx project if such changes happen.
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                    }
                    final int g = nibble2(opcodeUnit);
                    final int registerCount = nibble3(opcodeUnit);
                    final int methodIndex = in.read();
                    final int cdef = in.read();
                    final int c = nibble0(cdef);
                    final int d = nibble1(cdef);
                    final int e = nibble2(cdef);
                    final int f = nibble3(cdef);
                    final int protoIndex = in.read();
                    final int indexType = getInstructionIndexType(opcode);

                    if (registerCount < 1 || registerCount > 5) {
                        throw new DexException("bogus registerCount: " + Hex.uNibble(registerCount));
                    }

                    final int[] registers = Arrays.copyOfRange(new int[] {c, d, e, f, g}, 0, registerCount);

                    iv.visitInvokePolymorphicInstruction(currentAddress, opcode, methodIndex, indexType, protoIndex, registers);
                    break;
                }
                case INSN_FORMAT_4RCC: {
                    if (opcode != Opcodes.INVOKE_POLYMORPHIC_RANGE) {
                        // 4rcc isn't currently used for anything other than invoke-polymorphic.
                        // If that changes, add a more general DecodedInstruction for this format.
                        // TODO keep track on aosp dx project if such changes happen.
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                    }
                    final int registerCount = byte1(opcodeUnit);
                    final int methodIndex = in.read();
                    final int c = in.read();
                    final int protoIndex = in.read();
                    final int indexType = getInstructionIndexType(opcode);
                    iv.visitInvokePolymorphicRangeInstruction(currentAddress, opcode, methodIndex, indexType, c, registerCount, protoIndex);
                    break;
                }
                case INSN_FORMAT_PACKED_SWITCH_PAYLOAD: {
                    final int baseAddress = in.baseAddressForCursor();
                    final int size = in.read();
                    final int firstKey = in.readInt();
                    final int[] targets = new int[size];

                    for (int i = 0; i < size; i++) {
                        targets[i] = baseAddress + in.readInt();
                    }
                    iv.visitPackedSwitchPayloadInsn(currentAddress, opcodeUnit, firstKey, targets);
                    break;
                }
                case INSN_FORMAT_SPARSE_SWITCH_PAYLOAD: {
                    final int baseAddress = in.baseAddressForCursor();
                    final int size = in.read();
                    final int[] keys = new int[size];
                    final int[] targets = new int[size];

                    for (int i = 0; i < size; i++) {
                        keys[i] = in.readInt();
                    }

                    for (int i = 0; i < size; i++) {
                        targets[i] = baseAddress + in.readInt();
                    }

                    iv.visitSparseSwitchPayloadInsn(currentAddress, opcodeUnit, keys, targets);
                    break;
                }
                case INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD: {
                    final int elementWidth = in.read();
                    final int size = in.readInt();
                    switch (elementWidth) {
                        case 1: {
                            byte[] array = new byte[size];
                            boolean even = true;
                            for (int i = 0, value = 0; i < size; ++i, even = !even) {
                                if (even) {
                                    value = in.read();
                                }
                                array[i] = (byte) (value & 0xff);
                                value >>= 8;
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 1);
                            break;
                        }
                        case 2: {
                            short[] array = new short[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = (short) in.read();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 2);
                            break;
                        }
                        case 4: {
                            int[] array = new int[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = in.readInt();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 4);
                            break;
                        }
                        case 8: {
                            long[] array = new long[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = in.readLong();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 8);
                            break;
                        }
                        default: {
                            throw new DexException("bogus element_width: " + Hex.u2(elementWidth));
                        }
                    }
                    break;
                }
                default:
                    throw new DexException("Unknown instruction format: " + insnFormat);
            }
        }
    }

    public static void encode(ShortArrayCodeOutput out, InstructionWriter iw) {
        final int opcode = iw.currOpcode;
        final int insnFormat = getInstructionFormat(opcode);
        switch (insnFormat) {
            case INSN_FORMAT_00X:
            case INSN_FORMAT_10X: {
                out.write((short) opcode);
                break;
            }
            case INSN_FORMAT_12X: {
                out.write(codeUnit(opcode, makeByte(iw.currRegA, iw.currRegB)));
                break;
            }
            case INSN_FORMAT_11N: {
                out.write(codeUnit(opcode, makeByte(iw.currRegA, getLiteralNibble(iw.currLiteral))));
                break;
            }
            case INSN_FORMAT_11X: {
                out.write(codeUnit(opcode, iw.currRegA));
                break;
            }
            case INSN_FORMAT_10T: {
                final int relativeTarget = getTargetByte(iw.currTarget, out.cursor());
                out.write(codeUnit(opcode, relativeTarget));
                break;
            }
            case INSN_FORMAT_20T: {
                final short relativeTarget = getTargetUnit(iw.currTarget, out.cursor());
                out.write((short) opcode, relativeTarget);
                break;
            }
            case INSN_FORMAT_22X: {
                out.write(codeUnit(opcode, iw.currRegA), getBUnit(iw.currRegB));
                break;
            }
            case INSN_FORMAT_21T: {
                final short relativeTarget = getTargetUnit(iw.currTarget, out.cursor());
                out.write(codeUnit(opcode, iw.currRegA), relativeTarget);
                break;
            }
            case INSN_FORMAT_21S: {
                out.write(codeUnit(opcode, iw.currRegA), getLiteralUnit(iw.currLiteral));
                break;
            }
            case INSN_FORMAT_21H: {
                final int shift = (opcode == Opcodes.CONST_HIGH16) ? 16 : 48;
                final short literal = (short) (iw.currLiteral >> shift);
                out.write(codeUnit(opcode, iw.currRegA), literal);
                break;
            }
            case INSN_FORMAT_21C: {
                out.write(codeUnit(opcode, iw.currRegA), (short) iw.currIndex);
                break;
            }
            case INSN_FORMAT_23X: {
                out.write(codeUnit(opcode, iw.currRegA), codeUnit(iw.currRegB, iw.currRegC));
                break;
            }
            case INSN_FORMAT_22B: {
                out.write(codeUnit(opcode, iw.currRegA), codeUnit(iw.currRegB, getLiteralByte(iw.currLiteral)));
                break;
            }
            case INSN_FORMAT_22T: {
                final short relativeTarget = getTargetUnit(iw.currTarget, out.cursor());
                out.write(codeUnit(opcode, makeByte(iw.currRegA, iw.currRegB)), relativeTarget);
                break;
            }
            case INSN_FORMAT_22S: {
                out.write(codeUnit(opcode, makeByte(iw.currRegA, iw.currRegB)), getLiteralUnit(iw.currLiteral));
                break;
            }
            case INSN_FORMAT_22C: {
                out.write(codeUnit(opcode, makeByte(iw.currRegA, iw.currRegB)), (short) iw.currIndex);
                break;
            }
            case INSN_FORMAT_30T: {
                final int relativeTarget = getTarget(iw.currTarget, out.cursor());
                out.write((short) opcode, unit0(relativeTarget), unit1(relativeTarget));
                break;
            }
            case INSN_FORMAT_32X: {
                out.write((short) opcode, getAUnit(iw.currRegA), getBUnit(iw.currRegB));
                break;
            }
            case INSN_FORMAT_31I: {
                final int literal = getLiteralInt(iw.currLiteral);
                out.write(codeUnit(opcode, iw.currRegA), unit0(literal), unit1(literal));
                break;
            }
            case INSN_FORMAT_31T: {
                /*
                 * Switch instructions need to "forward" their addresses to their
                 * payload target instructions.
                 */
                switch (opcode) {
                    case Opcodes.PACKED_SWITCH:
                    case Opcodes.SPARSE_SWITCH: {
                        out.setBaseAddress(iw.currTarget, out.cursor());
                        break;
                    }
                    default: // fall out
                }
                final int relativeTarget = getTarget(iw.currTarget, out.cursor());
                out.write(codeUnit(opcode, iw.currRegA), unit0(relativeTarget), unit1(relativeTarget));
                break;
            }
            case INSN_FORMAT_31C: {
                final int index = iw.currIndex;
                out.write(codeUnit(opcode, iw.currRegA), unit0(index), unit1(index));
                break;
            }
            case INSN_FORMAT_35C: {
                out.write(codeUnit(opcode, makeByte(iw.currRegE, iw.currRegisterCount)),
                        (short) iw.currIndex, codeUnit(iw.currRegA, iw.currRegB, iw.currRegC, iw.currRegD));
                break;
            }
            case INSN_FORMAT_3RC: {
                out.write(codeUnit(opcode, iw.currRegisterCount), (short) iw.currIndex, getAUnit(iw.currRegA));
                break;
            }
            case INSN_FORMAT_51L: {
                final long literal = iw.currLiteral;
                out.write(codeUnit(opcode, iw.currRegA),
                        unit0(literal), unit1(literal), unit2(literal), unit3(literal));
                break;
            }
            case INSN_FORMAT_45CC: {
                out.write(codeUnit(opcode, makeByte(iw.currRegG, iw.currRegisterCount)), (short) iw.currIndex,
                        codeUnit(iw.currRegC, iw.currRegD, iw.currRegE, iw.currRegF), (short) iw.currProtoIndex);
                break;
            }
            case INSN_FORMAT_4RCC: {
                out.write(codeUnit(opcode, iw.currRegisterCount), (short) iw.currIndex,
                        getCUnit(iw.currRegC), (short) iw.currProtoIndex);
                break;
            }
            case INSN_FORMAT_PACKED_SWITCH_PAYLOAD: {
                final int[] targets = iw.currTargets;
                final int baseAddress = out.baseAddressForCursor();
                out.write((short) opcode);
                out.write(asUnsignedUnit(targets.length));
                out.writeInt(iw.currFirstKey);
                for (int target : targets) {
                    out.writeInt(target - baseAddress);
                }
                break;
            }
            case INSN_FORMAT_SPARSE_SWITCH_PAYLOAD: {
                final int[] keys = iw.currKeys;
                final int[] targets = iw.currTargets;
                final int baseAddress = out.baseAddressForCursor();
                out.write((short) opcode);
                out.write(asUnsignedUnit(targets.length));
                for (int key : keys) {
                    out.writeInt(key);
                }
                for (int target : targets) {
                    out.writeInt(target - baseAddress);
                }
                break;
            }
            case INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD: {
                final short elementWidth = (short) iw.currElementWidth;
                out.write((short) opcode);
                out.write(elementWidth);
                out.writeInt(iw.currSize);
                final Object data = iw.currData;
                switch (elementWidth) {
                    case 1: out.write((byte[]) data);  break;
                    case 2: out.write((short[]) data); break;
                    case 4: out.write((int[]) data);   break;
                    case 8: out.write((long[]) data);  break;
                    default: {
                        throw new DexException("bogus element_width: " + Hex.u2(elementWidth));
                    }
                }
                break;
            }
            default:
                throw new DexException("Unknown instruction format: " + insnFormat);
        }
    }

    public static short codeUnit(int lowByte, int highByte) {
        if ((lowByte & ~0xff) != 0) {
            throw new IllegalArgumentException("bogus lowByte");
        }

        if ((highByte & ~0xff) != 0) {
            throw new IllegalArgumentException("bogus highByte");
        }

        return (short) (lowByte | (highByte << 8));
    }

    public static short codeUnit(int nibble0, int nibble1, int nibble2,
                                 int nibble3) {
        if ((nibble0 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble0");
        }

        if ((nibble1 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble1");
        }

        if ((nibble2 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble2");
        }

        if ((nibble3 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble3");
        }

        return (short) (nibble0 | (nibble1 << 4)
                | (nibble2 << 8) | (nibble3 << 12));
    }

    public static int makeByte(int lowNibble, int highNibble) {
        if ((lowNibble & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus lowNibble");
        }

        if ((highNibble & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus highNibble");
        }

        return lowNibble | (highNibble << 4);
    }

    public static short asUnsignedUnit(int value) {
        if ((value & ~0xffff) != 0) {
            throw new IllegalArgumentException("bogus unsigned code unit");
        }

        return (short) value;
    }

    public static short unit0(int value) {
        return (short) value;
    }

    public static short unit1(int value) {
        return (short) (value >> 16);
    }

    public static short unit0(long value) {
        return (short) value;
    }

    public static short unit1(long value) {
        return (short) (value >> 16);
    }

    public static short unit2(long value) {
        return (short) (value >> 32);
    }

    public static short unit3(long value) {
        return (short) (value >> 48);
    }

    private static int byte0(int value) {
        return value & 0xff;
    }

    private static int byte1(int value) {
        return (value >> 8) & 0xff;
    }

    private static int nibble0(int value) {
        return value & 0xf;
    }

    private static int nibble1(int value) {
        return (value >> 4) & 0xf;
    }

    private static int nibble2(int value) {
        return (value >> 8) & 0xf;
    }

    private static int nibble3(int value) {
        return (value >> 12) & 0xf;
    }

    public static int getTargetByte(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (byte) relativeTarget) {
            throw new DexException(
                    "Target out of range: "
                            + Hex.s4(relativeTarget)
                            + ", perhaps you need to enable force jumbo mode."
            );
        }

        return relativeTarget & 0xff;
    }

    public static short getTargetUnit(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (short) relativeTarget) {
            throw new DexException(
                    "Target out of range: "
                            + Hex.s4(relativeTarget)
                            + ", perhaps you need to enable force jumbo mode."
            );
        }

        return (short) relativeTarget;
    }

    public static int getTarget(int target, int baseAddress) {
        return target - baseAddress;
    }

    public static int getLiteralByte(long literal) {
        if (literal != (byte) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xff;
    }

    public static short getLiteralUnit(long literal) {
        if (literal != (short) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (short) literal;
    }

    public static int getLiteralInt(long literal) {
        if (literal != (int) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal;
    }

    public static int getLiteralNibble(long literal) {
        if ((literal < -8) || (literal > 7)) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xf;
    }

    /**
     * Gets the A register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    public static short getAUnit(int a) {
        if ((a & ~0xffff) != 0) {
            throw new DexException("Register A out of range: " + Hex.u8(a));
        }

        return (short) a;
    }

    /**
     * Gets the B register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    public static short getBUnit(int b) {
        if ((b & ~0xffff) != 0) {
            throw new DexException("Register B out of range: " + Hex.u8(b));
        }

        return (short) b;
    }

    /**
     * Gets the C register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    public static short getCUnit(int c) {
        if ((c & ~0xffff) != 0) {
            throw new DexException("Register C out of range: " + Hex.u8(c));
        }

        return (short) c;
    }

    public static int getInstructionIndexType(int opcode) {
        switch (opcode) {
            case Opcodes.CONST_STRING:
            case Opcodes.CONST_STRING_JUMBO: {
                return INDEX_TYPE_STRING_REF;
            }
            case Opcodes.CONST_METHOD_HANDLE: {
                return INDEX_TYPE_METHOD_HANDLE_REF;
            }
            case Opcodes.CONST_METHOD_TYPE: {
                return INDEX_TYPE_PROTO_REF;
            }
            case Opcodes.CONST_CLASS:
            case Opcodes.CHECK_CAST:
            case Opcodes.INSTANCE_OF:
            case Opcodes.NEW_INSTANCE:
            case Opcodes.NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY_RANGE: {
                return INDEX_TYPE_TYPE_REF;
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
                return INDEX_TYPE_FIELD_REF;
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
                return INDEX_TYPE_METHOD_REF;
            }
            case Opcodes.INVOKE_POLYMORPHIC:
            case Opcodes.INVOKE_POLYMORPHIC_RANGE: {
                return INDEX_TYPE_METHOD_AND_PROTO_REF;
            }
            case Opcodes.INVOKE_CUSTOM:
            case Opcodes.INVOKE_CUSTOM_RANGE: {
                return INDEX_TYPE_CALL_SITE_REF;
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
                return INDEX_TYPE_NONE;
            }
            default: {
                return INDEX_TYPE_UNKNOWN;
            }
        }
    }

    public static int getInstructionFormat(int opcode) {
        switch (opcode) {
            case Opcodes.SPECIAL_FORMAT: {
                return INSN_FORMAT_00X;
            }
            case Opcodes.NOP:
            case Opcodes.RETURN_VOID: {
                return INSN_FORMAT_10X;
            }
            case Opcodes.MOVE:
            case Opcodes.MOVE_WIDE:
            case Opcodes.MOVE_OBJECT:
            case Opcodes.ARRAY_LENGTH:
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
            case Opcodes.REM_DOUBLE_2ADDR: {
                return INSN_FORMAT_12X;
            }
            case Opcodes.MOVE_FROM16:
            case Opcodes.MOVE_WIDE_FROM16:
            case Opcodes.MOVE_OBJECT_FROM16: {
                return INSN_FORMAT_22X;
            }
            case Opcodes.MOVE_16:
            case Opcodes.MOVE_WIDE_16:
            case Opcodes.MOVE_OBJECT_16: {
                return INSN_FORMAT_32X;
            }
            case Opcodes.MOVE_RESULT:
            case Opcodes.MOVE_RESULT_WIDE:
            case Opcodes.MOVE_RESULT_OBJECT:
            case Opcodes.MOVE_EXCEPTION:
            case Opcodes.RETURN:
            case Opcodes.RETURN_WIDE:
            case Opcodes.RETURN_OBJECT:
            case Opcodes.MONITOR_ENTER:
            case Opcodes.MONITOR_EXIT:
            case Opcodes.THROW: {
                return INSN_FORMAT_11X;
            }
            case Opcodes.CONST_4: {
                return INSN_FORMAT_11N;
            }
            case Opcodes.CONST_16:
            case Opcodes.CONST_WIDE_16: {
                return INSN_FORMAT_21S;
            }
            case Opcodes.CONST:
            case Opcodes.CONST_WIDE_32: {
                return INSN_FORMAT_31I;
            }
            case Opcodes.CONST_HIGH16:
            case Opcodes.CONST_WIDE_HIGH16: {
                return INSN_FORMAT_21H;
            }
            case Opcodes.CONST_WIDE: {
                return INSN_FORMAT_51L;
            }
            case Opcodes.CONST_STRING:
            case Opcodes.CONST_CLASS:
            case Opcodes.CHECK_CAST:
            case Opcodes.NEW_INSTANCE:
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
            case Opcodes.SPUT_SHORT:
            case Opcodes.CONST_METHOD_HANDLE:
            case Opcodes.CONST_METHOD_TYPE: {
                return INSN_FORMAT_21C;
            }
            case Opcodes.CONST_STRING_JUMBO: {
                return INSN_FORMAT_31C;
            }
            case Opcodes.INSTANCE_OF:
            case Opcodes.NEW_ARRAY:
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
            case Opcodes.IPUT_SHORT: {
                return INSN_FORMAT_22C;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE:
            case Opcodes.INVOKE_CUSTOM: {
                return INSN_FORMAT_35C;
            }
            case Opcodes.FILLED_NEW_ARRAY_RANGE:
            case Opcodes.INVOKE_VIRTUAL_RANGE:
            case Opcodes.INVOKE_SUPER_RANGE:
            case Opcodes.INVOKE_DIRECT_RANGE:
            case Opcodes.INVOKE_STATIC_RANGE:
            case Opcodes.INVOKE_INTERFACE_RANGE:
            case Opcodes.INVOKE_CUSTOM_RANGE: {
                return INSN_FORMAT_3RC;
            }
            case Opcodes.FILL_ARRAY_DATA:
            case Opcodes.PACKED_SWITCH:
            case Opcodes.SPARSE_SWITCH: {
                return INSN_FORMAT_31T;
            }
            case Opcodes.GOTO: {
                return INSN_FORMAT_10T;
            }
            case Opcodes.GOTO_16: {
                return INSN_FORMAT_20T;
            }
            case Opcodes.GOTO_32: {
                return INSN_FORMAT_30T;
            }
            case Opcodes.CMPL_FLOAT:
            case Opcodes.CMPG_FLOAT:
            case Opcodes.CMPL_DOUBLE:
            case Opcodes.CMPG_DOUBLE:
            case Opcodes.CMP_LONG:
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
            case Opcodes.REM_DOUBLE: {
                return INSN_FORMAT_23X;
            }
            case Opcodes.IF_EQ:
            case Opcodes.IF_NE:
            case Opcodes.IF_LT:
            case Opcodes.IF_GE:
            case Opcodes.IF_GT:
            case Opcodes.IF_LE: {
                return INSN_FORMAT_22T;
            }
            case Opcodes.IF_EQZ:
            case Opcodes.IF_NEZ:
            case Opcodes.IF_LTZ:
            case Opcodes.IF_GEZ:
            case Opcodes.IF_GTZ:
            case Opcodes.IF_LEZ: {
                return INSN_FORMAT_21T;
            }
            case Opcodes.ADD_INT_LIT16:
            case Opcodes.RSUB_INT:
            case Opcodes.MUL_INT_LIT16:
            case Opcodes.DIV_INT_LIT16:
            case Opcodes.REM_INT_LIT16:
            case Opcodes.AND_INT_LIT16:
            case Opcodes.OR_INT_LIT16:
            case Opcodes.XOR_INT_LIT16: {
                return INSN_FORMAT_22S;
            }
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
                return INSN_FORMAT_22B;
            }
            case Opcodes.INVOKE_POLYMORPHIC: {
                return INSN_FORMAT_45CC;
            }
            case Opcodes.INVOKE_POLYMORPHIC_RANGE: {
                return INSN_FORMAT_4RCC;
            }
            case Opcodes.PACKED_SWITCH_PAYLOAD: {
                return INSN_FORMAT_PACKED_SWITCH_PAYLOAD;
            }
            case Opcodes.SPARSE_SWITCH_PAYLOAD: {
                return INSN_FORMAT_SPARSE_SWITCH_PAYLOAD;
            }
            case Opcodes.FILL_ARRAY_DATA_PAYLOAD: {
                return INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD;
            }
            default: {
                return INSN_FORMAT_UNKNOWN;
            }
        }
    }
}
