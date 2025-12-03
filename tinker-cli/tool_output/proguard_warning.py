#!/usr/bin/python
# coding: utf8

import os
import sys


def print_usage():
    print >>sys.stderr, \
        """usage: python proguard_warning.py mapping.txt warning.txt

           the output mapping file is 'mapping_edit.txt' in the cwd directory
        """
    sys.exit(1)

class MappingData:
    raw_line = ""
    key = ""
    filed_methods = []

    def __init__(self):
        self.raw_line = ""
        self.key = ""
        self.filed_methods = []

class RemoveProguardWarning:
    def __init__(self):
        self.classes = {}
        self.class_list = []

    def read_mapping_file(self, mapping):
        current_mapping_data = None
        with open(mapping) as fd:
            for line in fd.readlines():
                if not line.startswith(' '):
                    if current_mapping_data is not None:
                        self.classes[current_mapping_data.key] = current_mapping_data
                        self.class_list.append(current_mapping_data.key)

                    current_mapping_data = MappingData()
                    current_mapping_data.raw_line = line
                    current_mapping_data.key = line.split('->')[0].strip()
                else:
                    current_mapping_data.filed_methods.append(line)

            self.classes[current_mapping_data.key] = current_mapping_data
            self.class_list.append(current_mapping_data.key)

        # print "size: ", len(self.classes)

    def remove_warning(self, warning):
        with open(warning) as fd:
            for line in fd.readlines():
                if not line.startswith("Warning:"):
                    raise Exception("proguard warning must begin with 'Warning:', line=", line)
                splits = line.split(':')
                class_key = splits[1].strip()
                # print "class_key", class_key
                if class_key not in self.classes:
                    print "Warning:can't find warning class in the mapping file, class=", class_key
                    continue
                warning_value = splits[2].split("'")[1] + " -> " + splits[2].split("'")[5]
                mapping_data = self.classes[class_key]
                # print "warning_value", warning_value

                find = False
                for mappings in mapping_data.filed_methods:
                    if mappings.find(warning_value) != -1:
                        mapping_data.filed_methods.remove(mappings)
                        find = True
                        break

                if not find:
                    print "Warning: can't find warning field or method in the mapping file:', value=", warning_value

                if len(mapping_data.filed_methods) == 0:
                    del self.classes[class_key]

        output_path = os.path.join(os.getcwd(), "mapping_edit.txt")
        with open(output_path, "w") as fw:
            for key in self.class_list:
                if key in self.classes:
                    data = self.classes[key]
                    fw.write(data.raw_line)
                    for line in data.filed_methods:
                        fw.write(line)

    def remove_warning_mapping(self, mapping, warning):
        self.read_mapping_file(mapping)
        self.remove_warning(warning)

    def do_command(self, args):
        if (len(args) < 2):
            print_usage()

        mapping_path = args[0]
        if not os.path.exists(mapping_path):
            raise Exception("mapping file is not exist, path=%s", mapping_path)

        warning_patch = args[1]
        if not os.path.exists(warning_patch):
            raise Exception("proguard warning file is not exist, path=%s", warning_patch)

        self.remove_warning_mapping(mapping_path, warning_patch)



if __name__ == '__main__':
    RemoveProguardWarning().do_command(sys.argv[1:])