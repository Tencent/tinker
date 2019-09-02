#!/bin/bash
helpme()
{
    echo "Build patch zip"
    echo "USAGE:"
    echo "       ./build_patch_dexdiff.sh [old=old.apk path] [new=new.apk path]"
    echo "EXAMPLE:"
    echo "       ./build_patch_dexdiff.sh old=/old.apk new=/new.apk"
    exit 1
}

parse_cmdline()
{
    while [[ -n "$1" ]]
    do
        OPTIONS=`echo "$1" | sed 's/\(.*\)=\(.*\)/\1/'`
        PARAM=`echo "$1" | sed 's/.*=//'`
        if [[ "$1" != *=* ]];then
            helpme
            CHECK_FLAG=0
        fi
        case "$OPTIONS" in
        old)     OLD_APK_PATH=${PARAM};;
        new)     NEW_APK_PATH=${PARAM};;
        #please add extra parameter here!
        *)  if [[ `echo "$1" | sed -n "/.*=/p"` ]];then
                echo "Error, the pattem \"$OPTIONS=${PARAM}\" can not be recognized!!!"
                helpme
            fi
            break;;
        esac
        shift
    done
    COMMAND_ARGS=$@
}

initParameter()
{
    OLD_FILE=`pwd`/old_file
    NEW_FILE=`pwd`/new_file
}

dexdiff()
{
    mkdir ${OLD_FILE}
    pushd ${OLD_FILE}
    unzip -o ${OLD_APK_PATH} -d ${OLD_FILE}
    OLD_DEX_COUNT=`ls | grep classes | wc -l`
    OLD_DEX_SET=${OLD_FILE}/classes.dex
    for count in `seq 2 ${OLD_DEX_COUNT}`
    do
        OLD_DEX_SET="${OLD_DEX_SET} ${OLD_FILE}/classes$count.dex"
    done
    popd

    mkdir ${NEW_FILE}
    pushd ${NEW_FILE}
    unzip -o ${NEW_APK_PATH} -d ${NEW_FILE}
    NEW_DEX_COUNT=`ls | grep classes | wc -l`
    NEW_DEX_SET=${NEW_FILE}/classes.dex
    for count in `seq 2 ${NEW_DEX_COUNT}`
    do
        NEW_DEX_SET="${NEW_DEX_SET} ${NEW_FILE}/classes$count.dex"
    done
    popd

    dexcmp -n ${NEW_DEX_SET} -o ${OLD_DEX_SET} -f `pwd`/patch.dex
}

dexcmp()
{
    java -cp "./DexCmp.jar" "io.qivaz.dex.cmp.DexCmp" "$@"
}

zipPatch()
{
    zip -m0 patch.apk ./patch.dex
    zip -m0 patch.apk ./new_file/classes.dex
    for count in `seq 2 ${NEW_DEX_COUNT}`
    do
        zip -m0 patch.apk ./new_file/classes${count}.dex
    done
}

parse_cmdline $@
initParameter
dexdiff
zipPatch
