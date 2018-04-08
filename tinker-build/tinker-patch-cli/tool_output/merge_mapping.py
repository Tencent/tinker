#!/usr/bin/python
# coding: utf-8
"""
当工程使用了applymapping之后，会遇到这样的问题
    1.类和方法上个版本被keep住了，这个版本不keep
    2.类和方法上个版本没有被keep住，这个版本又keep住了
这两个问题会导致proguard报warning，官方建议是手动解决冲突
(http://proguard.sourceforge.net/manual/troubleshooting.html#mappingconflict1)
不解决的话默认以mapping文件为最高优先级处理，这样混淆会带来一些问题

该方案为
简单来说，上个版本的mapping称为mapping1，直接编译当前项目，获取当前的mapping文件，称为mapping2。
从mapping2中可以得到当前项目需要混淆的类，因为重用mapping的意义在于同样的类在不同版本中混淆后的名称保持一致性，
所以将mapping2里面的所有类和方法统统在mapping1中去查找对应的混淆名称，生成新的mapping3，找不到则不写入mapping3，
然后mapping3就是最后可以使用的mapping文件。最后重用mapping3来编译当前项目，完成打包整个过程

最后生成的mapping会保留当前版本和之前版本都需要混淆的类和方法，且混淆后的名字取之前的mapping版本中的名字，
对于keep状态冲突的类和方法，处理方式是不保留在新生成的mapping中，编译过程中由当前的proguard的配置文件来处理
so  新生成的mapping是之前版本mapping的一个子集

使用教程是传入上版本的mapping和当前项目未applymapping得到的mapping文件，输出处理后的mapping 文件。
"""
import os
import sys


def print_usage():
    print >>sys.stderr, \
        """usage: python merge_mapping.py old_mapping.txt current_mapping.txt
           the output mapping file is 'new_mapping.txt' in the cwd directory
        """
    sys.exit(1)


class MappingData:
    def __init__(self):
        self.raw_line = ""
        self.key = ""
        self.field_methods = []


class DealWithProguardWarning:
    def __init__(self):
        self.classes = {}
        self.class_list = []
        self.current_classes = {}
        self.current_class_list = []

    @staticmethod
    def read_mapping_file(classes, class_list, mapping):
        current_mapping_data = None
        with open(mapping, 'r') as fd:
            # 一行一行读取
            for line in fd.xreadlines():
                # 如果不是空格开头，类的处理
                if not line.startswith(' '):
                    # 对象不为空，先保存之前的
                    if current_mapping_data is not None:
                        classes[current_mapping_data.key] = current_mapping_data
                        class_list.append(current_mapping_data.key)
                    # 重新创建对象，并赋值
                    current_mapping_data = MappingData()
                    current_mapping_data.raw_line = line
                    current_mapping_data.key = line.split('->')[0].strip()
                else:
                    # 方法的处理，直接加进去
                    current_mapping_data.field_methods.append(line)
            classes[current_mapping_data.key] = current_mapping_data
            class_list.append(current_mapping_data.key)
        print "size: ", len(classes)

    def remove_warning_mapping(self, old_mapping, current_mapping):
        self.read_mapping_file(self.classes, self.class_list, old_mapping)
        self.read_mapping_file(self.current_classes, self.current_class_list, current_mapping)
        self.do_merge()
        self.print_new_mapping()

    def exe(self, args):
        if len(args) < 2:
            print_usage()

        old_mapping_path = args[0]
        if not os.path.exists(old_mapping_path):
            raise Exception("mapping file is not exist, path=%s", old_mapping_path)

        current_mapping_path = args[1]
        if not os.path.exists(current_mapping_path):
            raise Exception("proguard warning file is not exist, path=%s", current_mapping_path)

        self.remove_warning_mapping(old_mapping_path, current_mapping_path)

    def do_merge(self):
        # 遍历当前的mapping class_key
        for key in self.current_class_list:
            if key in self.classes:
                data = self.classes[key]
                current_data = self.current_classes[key]
                # 如果当前的类没有被混淆，则保留，否则用之前的mapping里面的内容覆盖
                # ___.___ -> ___.___:
                if current_data.raw_line.split("->")[0] != current_data.raw_line.split("->")[1][:-1]:
                    current_data.raw_line = data.raw_line
                new_method_list = []
                # 处理方法
                for line in current_data.field_methods:
                    result, new_line = self.find_same_methods(line, data)
                    # 只有找到才写入
                    if result:
                        new_method_list.append(new_line)
                current_data.field_methods = new_method_list
            # 新的混淆不在旧的里面，则删除
            else:
                del self.current_classes[key]

    def find_same_methods(self, line, data):
        search_name, search_complete_name, search_new_name = self.get_name_and_complete_name_and_new_name(line)
        # 这里是特殊情况，如果在当前mapping发现查找的这个并没有混淆，就不打算保留在mapping文件中
        if search_name == search_new_name:
            return False, ""
        for method_line in data.field_methods:
            target_name, target_complete_name, target_new_name = self.get_name_and_complete_name_and_new_name(method_line)
            # 这里必须要用最完整的信息来进行比较，避免重载的影响
            if search_complete_name == target_complete_name:
                print "1"
                return True, method_line
            print "0"
        return False, ""

    # 返回名字 包含返回值和参数的名字 和 混淆后的名字
    @staticmethod
    def get_name_and_complete_name_and_new_name(line):
        """ ___ ___ -> ___
            ___:___:___ ___(___) -> ___
            ___:___:___ ___(___):___ -> ___
            ___:___:___ ___(___):___:___ -> ___
        """
        no_space_line = line.strip()
        colonIndex1 = no_space_line.find(":")
        colonIndex2 = no_space_line.find(":", colonIndex1+1) if colonIndex1 != -1 else -1
        spaceIndex = no_space_line.find(" ", colonIndex2+2)
        argumentIndex1 = no_space_line.find("(", spaceIndex+1)
        argumentIndex2 = no_space_line.find(")", argumentIndex1+1) if argumentIndex1 != -1 else -1
        colonIndex3 = no_space_line.find(":", argumentIndex2+1) if argumentIndex2 != -1 else -1
        colonIndex4 = no_space_line.find(":", colonIndex3+1) if colonIndex3 != -1 else -1
        arrowIndex = no_space_line.find("->")

        if spaceIndex < 0 or arrowIndex < 0:
            raise Exception("can not parse line %s", no_space_line)
        name = no_space_line[spaceIndex + 1: argumentIndex1 if argumentIndex1 >= 0 else arrowIndex].strip()
        new_name = no_space_line[arrowIndex + 2:].strip()
        complete_name = no_space_line[colonIndex2 + 1:arrowIndex].strip()
        return name, complete_name,  new_name

    def print_new_mapping(self):
        output_path = os.path.join(os.getcwd(), "new_mapping.txt")
        with open(output_path, "w") as fw:
            for key in self.current_class_list:
                if key in self.current_classes:
                    data = self.current_classes[key]
                    fw.write(data.raw_line)
                    for line in data.field_methods:
                        fw.write(line)


if __name__ == '__main__':
    DealWithProguardWarning().exe(sys.argv[1:])
