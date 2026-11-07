#!/system/bin/sh
#
# Copyright (C) 2026 kimal
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

am start --user 0 -n com.luminatweaks/com.luminatweaks.MainActivity >/dev/null 2>&1 || monkey -p com.luminatweaks -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
