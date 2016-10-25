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

package com.tencent.tinker.server.utils;

import android.content.Context;
import android.text.TextUtils;

import com.tencent.tinker.lib.util.TinkerLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import static com.tencent.tinker.server.TinkerClientImp.TAG;


/**
 * Created by sun on 11/10/2016.
 */

public class Conditions {

    static final String FILE_NAME = "CONDITIONS_MAP";
    static final Pattern INT_PATTERN = Pattern.compile("-?[0-9]+");

    private final Map<String, String> properties;

    public Conditions(Context context) {
        properties = read(context);
    }

    public Boolean check(String rules) {
        if (TextUtils.isEmpty(rules)) {
            return true;
        }
        List<String> rpList = Helper.toReversePolish(rules);
        try {
            return Helper.calcReversePolish(rpList, properties);
        } catch (Exception ignore) {
            TinkerLog.e(TAG, "parse conditions error(have you written '==' as '='?): " + rules);
            TinkerLog.w(TAG, "exception:" + ignore);
            return false;
        }
    }

    /**
     * set the k,v to conditions map.
     * you should invoke {@link #save(Context)} for saving the map to disk
     * @param key the key
     * @param value the value
     * @return {@link Conditions} this
     */
    public Conditions set(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Clean all properties. you should invoke {@link #save(Context)} for saving to disk.
     * @return {@link Conditions} this
     */
    public Conditions clean() {
        properties.clear();
        return this;
    }

    /**
     * save to disk
     * @param context {@link Context}
     * @throws IOException
     */
    public void save(Context context) throws IOException {
        File file = new File(context.getFilesDir(), FILE_NAME);
        ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
        outputStream.writeObject(properties);
        outputStream.flush();
        outputStream.close();
    }

    private HashMap<String, String> read(Context context) {

        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                HashMap<String, String> map = (HashMap<String, String>) ois.readObject();
                ois.close();
                return map;
            }
        } catch (Exception ignore) {
            ignore.printStackTrace();
        }
        return new HashMap<>();
    }

    static final class Helper {
        private static final String WITH_DELIMITER = "((?<=[%1$s])|(?=[%1$s]))";
        private static final List<String> TOKENS = new ArrayList<>(4);
        private static final HashMap<String, Integer> TOKEN_PRIORITY = new HashMap<>();

        static {
            TOKENS.add("&");
            TOKENS.add("|");
            TOKENS.add("(");
            TOKENS.add(")");

            TOKEN_PRIORITY.put("&", 2);
            TOKEN_PRIORITY.put("|", 1);
            TOKEN_PRIORITY.put("(", 3);
            TOKEN_PRIORITY.put(")", 3);
        }

        private Helper() {
            // A Util Class
        }

        public static List<String> toReversePolish(String input) {
            Stack<String> opStack = new Stack<>();
            List<String> rpList = new LinkedList<>();
            for (String word : tokenize(input)) {
                if (isToken(word)) {
                    pushOp(opStack, rpList, word);
                } else {
                    rpList.add(word);
                }
            }
            while (!opStack.isEmpty()) {
                rpList.add(opStack.pop());
            }
            return rpList;
        }

        private static void pushOp(Stack<String> stack, List<String> rpList, String op) {
            if (stack.isEmpty() || "(".equals(op)) {
                stack.push(op);
                return;
            }

            if (")".equals(op)) {
                String tmp;
                while (!"(".equals(tmp = stack.pop())) {
                    rpList.add(tmp);
                }
                return;
            }
            if ("(".equals(stack.peek())) {
                stack.push(op);
                return;
            }

            if (TOKEN_PRIORITY.get(op) > TOKEN_PRIORITY.get(stack.peek())) {
                stack.push(op);
            } else {
                rpList.add(stack.pop());
                pushOp(stack, rpList, op);
            }
        }

        public static Boolean calcReversePolish(List<String> list, Map<String, String> props) {
            Stack<Object> stack = new Stack<>();
            for (String word : list) {
                if (!isToken(word)) {
                    // lazy calcExpr at pop from stack, some expr needn't calculate.
                    // such 'true || expr'
                    stack.push(word);
                } else {
                    Boolean left, right;
                    Object v1, v2;
                    switch (word) {
                        case "|":
                            v1 = stack.pop();
                            v2 = stack.pop();
                            left = calcExpr(v1, props);
                            if (left) {
                                stack.push(Boolean.TRUE);
                                continue;
                            }
                            right = calcExpr(v2, props);
                            stack.push(right);
                            break;
                        case "&":
                            v1 = stack.pop();
                            v2 = stack.pop();
                            left = calcExpr(v1, props);
                            if (!left) {
                                stack.push(Boolean.FALSE);
                                continue;
                            }
                            right = calcExpr(v2, props);
                            stack.push(right);
                            break;
                        default:
                            throw new RuntimeException("Unsupported Operator: " + word);
                    }
                }
            }
            return calcExpr(stack.pop(), props);
        }

        public static Boolean calcExpr(Object obj, Map<String, String> props) {
            if (obj instanceof String) {
                return calcExpr((String) obj, props);
            } else if (obj instanceof Boolean) {
                return (Boolean) obj;
            } else {
                throw new RuntimeException("illegal type pass to calcExpr");
            }
        }

        public static Boolean calcExpr(String expr, Map<String, String> props) {
            boolean isInProps = false;
            List<String> exprList = splitExpr(expr);
            String op = exprList.get(1);
            String left = exprList.get(0);
            String right = exprList.get(2);
            if (props.containsKey(left)) {
                isInProps = true;
                left = props.get(left);
            }
            if (props.containsKey(right)) {
                isInProps = true;
                right = props.get(right);
            }
            return isInProps && calcExpr(left, right, op);
        }

        public static Boolean calcExpr(String left, String right, String op) {
            switch (op) {
                case "==":
                    return left.equals(right);
                case "!=":
                    return !left.equals(right);
                case ">=":
                    if (isInt(left)) {
                        return Integer.parseInt(left) >= Integer.parseInt(right);
                    } else {
                        return left.compareToIgnoreCase(right) >= 0;
                    }
                case ">":
                    if (isInt(left)) {
                        return Integer.parseInt(left) > Integer.parseInt(right);
                    } else {
                        return left.compareToIgnoreCase(right) > 0;
                    }
                case "<=":
                    if (isInt(left)) {
                        return Integer.parseInt(left) <= Integer.parseInt(right);
                    } else {
                        return left.compareToIgnoreCase(right) <= 0;
                    }
                case "<":
                    if (isInt(left)) {
                        return Integer.parseInt(left) < Integer.parseInt(right);
                    } else {
                        return left.compareToIgnoreCase(right) < 0;
                    }
                default:
                    throw new RuntimeException("Unsupported Operator");
            }
        }

        public static List<String> splitExpr(String expr) {
            String[] ops = new String[] {"==", "!=", ">=", "<=", ">", "<"};
            for (String op : ops) {
                if (expr.contains(op)) {
                    int pos = expr.indexOf(op);
                    String left = expr.substring(0, pos);
                    String right = expr.substring(pos + op.length(), expr.length());
                    return Arrays.asList(left, op, right);
                }
            }
            return new ArrayList<>();
        }

        private static Boolean isToken(String word) {
            return TOKENS.contains(word);
        }

        private static List<String> tokenize(String input) {
            input = input.replaceAll("\\s+", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&&", "&").replaceAll("\\|\\|", "|");
            List<String> tokens = new ArrayList<>(TOKENS.size());
            for (String token : TOKENS) {
                tokens.add(Pattern.quote(token));
            }
            String splits = TextUtils.join("|", tokens);
            return Arrays.asList(input.split(String.format(WITH_DELIMITER, splits)));
        }

        private static Boolean isInt(String string) {
            return INT_PATTERN.matcher(string).matches();
        }
    }
}
