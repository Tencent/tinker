/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.android.dex.util;

import java.util.Comparator;

/**
 * *** This file is NOT a part of AOSP. ***
 * Created by tangyinsheng on 2016/6/28.
 */
public final class CompareUtils {
    private CompareUtils() { }

    public static int uCompare(byte ubyteA, byte ubyteB) {
        if (ubyteA == ubyteB) {
            return 0;
        }
        int a = ubyteA & 0xFF;
        int b = ubyteB & 0xFF;
        return a < b ? -1 : 1;
    }

    public static int uCompare(short ushortA, short ushortB) {
        if (ushortA == ushortB) {
            return 0;
        }
        int a = ushortA & 0xFFFF;
        int b = ushortB & 0xFFFF;
        return a < b ? -1 : 1;
    }

    public static int uCompare(int uintA, int uintB) {
        if (uintA == uintB) {
            return 0;
        }
        long a = uintA & 0xFFFFFFFFL;
        long b = uintB & 0xFFFFFFFFL;
        return a < b ? -1 : 1;
    }

    public static int uArrCompare(byte[] ubyteArrA, byte[] ubyteArrB) {
        int lenA = ubyteArrA.length;
        int lenB = ubyteArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = uCompare(ubyteArrA[i], ubyteArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int uArrCompare(short[] ushortArrA, short[] ushortArrB) {
        int lenA = ushortArrA.length;
        int lenB = ushortArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = uCompare(ushortArrA[i], ushortArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int uArrCompare(int[] uintArrA, int[] uintArrB) {
        int lenA = uintArrA.length;
        int lenB = uintArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = uCompare(uintArrA[i], uintArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int sCompare(byte sbyteA, byte sbyteB) {
        if (sbyteA == sbyteB) {
            return 0;
        }
        return sbyteA < sbyteB ? -1 : 1;
    }

    public static int sCompare(short sshortA, short sshortB) {
        if (sshortA == sshortB) {
            return 0;
        }
        return sshortA < sshortB ? -1 : 1;
    }

    public static int sCompare(int sintA, int sintB) {
        if (sintA == sintB) {
            return 0;
        }
        return sintA < sintB ? -1 : 1;
    }

    public static int sCompare(long slongA, long slongB) {
        if (slongA == slongB) {
            return 0;
        }
        return slongA < slongB ? -1 : 1;
    }

    public static int sArrCompare(byte[] sbyteArrA, byte[] sbyteArrB) {
        int lenA = sbyteArrA.length;
        int lenB = sbyteArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = sCompare(sbyteArrA[i], sbyteArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int sArrCompare(short[] sshortArrA, short[] sshortArrB) {
        int lenA = sshortArrA.length;
        int lenB = sshortArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = sCompare(sshortArrA[i], sshortArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int sArrCompare(int[] sintArrA, int[] sintArrB) {
        int lenA = sintArrA.length;
        int lenB = sintArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = sCompare(sintArrA[i], sintArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static int sArrCompare(long[] slongArrA, long[] slongArrB) {
        int lenA = slongArrA.length;
        int lenB = slongArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = sCompare(slongArrA[i], slongArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static <T extends Comparable<T>> int aArrCompare(T[] aArrA, T[] aArrB) {
        int lenA = aArrA.length;
        int lenB = aArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = aArrA[i].compareTo(aArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

    public static <T> int aArrCompare(T[] aArrA, T[] aArrB, Comparator<T> cmptor) {
        int lenA = aArrA.length;
        int lenB = aArrB.length;
        if (lenA < lenB) {
            return -1;
        } else
        if (lenA > lenB) {
            return 1;
        } else {
            for (int i = 0; i < lenA; ++i) {
                int res = cmptor.compare(aArrA[i], aArrB[i]);
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }
}
