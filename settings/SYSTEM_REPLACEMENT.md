# Future JP5S Settings role

Beta 3 removes runtime handoffs to the installed stock Settings frontend. Keep
using the side-by-side signed beta while validating on the JP5S. SettingsProvider
and the Android system services remain unchanged.

## Experimental build profile

After building `:settings:assembleDebug`, assemble an **unsigned** candidate:

```sh
python3 tools/settings/assemble_jp5s.py \
  --input /path/to/the/supplied/Settings.apk \
  --apktool /path/to/apktool_2.12.1.jar \
  --work work/jp5s-replacement --mode replacement
```

The output is `work/jp5s-replacement/jp5s-unsigned.apk`. The dependency audit is
written beside it. This profile uses `com.android.settings`, shared system UID,
version code 10004 and the original public system intent/receiver contracts.
Canonical Activity aliases retain legacy explicit component entry points and
forward to bundled implementations. Common Settings actions open Grapefruit's
pages; device-specific actions use the relocated JP5S pages. It is a firmware
integration candidate, not the APK offered for ordinary installation.

No replacement candidate is signed or published as an installable download.
No commands here disable or remove stock Settings, alter system partitions,
change SettingsProvider, or modify an attached device.

## Remaining integration work

Validate Wi-Fi, Bluetooth pairing, lock creation/confirmation, accounts, storage,
USB/HDMI, system intent entry points and behavior after reboot on the JP5S. Unit
checks and static audits cannot verify privileged OEM calls or hardware behavior.
The data-installed test app also lacks the system-image placement used by some
OEM feature checks. A future system-image installation needs the firmware's
matching signing identity, permissions and a recovery path.

The theme provider deliberately keeps
`io.github.hayliewordsman.grapefruitsettings.theme` so installed Grapefruit clients
continue using the same contract. Android permits only one owner of that
authority: migrating from the test package to the canonical package needs an
explicit preferences/theme migration and removal of the old provider owner at
cutover. That transition is not performed by this build.

The original uploaded Settings.apk remains the build seed. All its required
frontend code is embedded in the output; it does not need to remain installed
for Grapefruit's internal navigation. Optional account/authentication and other
platform applications remain separate Android services, as with stock Settings.
