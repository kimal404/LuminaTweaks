killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null

rm -rf /data/adb/luminatweaks
rm -f /dev/socket/luminad* 2>/dev/null

pm uninstall --user 0 com.luminatweaks >/dev/null 2>&1
pm uninstall com.luminatweaks >/dev/null 2>&1
cmd package uninstall com.luminatweaks >/dev/null 2>&1

exit 0
