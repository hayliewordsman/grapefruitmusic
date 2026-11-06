# SPDX-License-Identifier: Apache-2.0
$(call inherit-product, device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk)
$(call inherit-product, vendor/grapefruit/os/products/common.mk)
PRODUCT_NAME := grapefruit_cf_x86_64_phone
PRODUCT_MODEL := Grapefruit OS Cuttlefish
PRODUCT_MANUFACTURER := Grapefruit
