# SPDX-License-Identifier: Apache-2.0
GRAPEFRUIT_PATH := vendor/grapefruit
$(call inherit-product, $(GRAPEFRUIT_PATH)/os/products/edition.mk)
PRODUCT_BRAND := Grapefruit
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += ro.grapefruit.version=0.1-dev
PRODUCT_PACKAGES += LatinIME GrapefruitThemeWallpaper \
    Geist-Regular.ttf Geist-Medium.ttf Geist-SemiBold.ttf Geist-Bold.ttf
DEVICE_PACKAGE_OVERLAYS += $(GRAPEFRUIT_PATH)/os/overlay/common
# The OEM may explicitly supply a separate, private product makefile in its own tree.
# Neither the FOSS product nor CI downloads Google Apps or proprietary device files.
