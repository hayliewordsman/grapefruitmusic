# Neway N-XR bring-up

The N-XR is the intended commercial hardware target. It is not yet a supported
flash target. `profile.json` records the published listing without correcting
its contradictory specifications or inventing a device codename, partition map,
kernel configuration, or vendor interface.

The [product listing](https://www.neway.mobi/n-xr.html) names MT6580, a legacy
Cortex-A7/32-bit platform. That is a major BSP and driver concern for Android 17.
This is not a claim that 32-bit alone prohibits every AOSP 17 configuration.
No Android 17 BSP or certification evidence for this board has been provided.
An updated supported board in the N-XR enclosure is a more practical production
route if the listed MT6580 is the actual offer.

The page says 4G but lists GSM/WCDMA bands, lists “IOS11” as its OS, calls storage
“16MB”, and gives two battery capacities. None establishes the actual Android
version, usable RAM, flash capacity, LTE/VoLTE capability, or Google certification.
Its face-recognition marketing also does not establish Android biometric security
class or suitability for payment authentication.

Request these deliverables from Neway through your existing OEM discussion:

1. Exact PCBA revision, SoC/SKU, Android board codename, actual RAM/flash options,
   display/touch controller, notch/cutout geometry, density, and camera modules.
2. Android 17 BSP and maintained kernel source/defconfig, vendor HALs and VINTF
   manifests, graphics/composer and codec support, modem/IMS firmware, camera,
   audio, sensors, power, thermal, Wi-Fi/Bluetooth and GNSS support.
3. Boot image format, partition layout, dynamic partitions and update mechanism,
   AVB/rollback requirements, supported recovery/unbrick workflow, and an
   engineering sample with OEM-authorized bootloader access.
4. Final-build GMS licensing/certification plan, compatibility evidence for the
   quoted board, supported LTE/VoLTE bands for the intended markets, and the
   kernel/security maintenance commitment.

Keep proprietary BSP inputs and signing keys in the OEM's separate tree. Publish
the required corresponding source and notices for redistributed FOSS components.
Google Apps remain a separately licensed OEM addition to the final signed image.

Once the BSP arrives, introduce a real device product that inherits
`vendor/grapefruit/os/products/common.mk`; use the OEM's actual board settings.
Do not use the Cuttlefish image, a guessed scatter file, or another MT6580
device's partition map on the N-XR.

The initial performance gate is smooth Home/Recents and shade interaction on the
actual panel, with cold-start, PSS, GPU frame-time, thermal, and battery evidence.
Test low-memory eviction and restoration with Audio, TV and the Victory build.
Select low-RAM flags and blur/animation budgets from the measured board and its
Android compatibility requirements, rather than the product page's RAM label.
