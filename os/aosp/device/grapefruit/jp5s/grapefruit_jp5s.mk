$(call inherit-product, build/target/product/full_base.mk)
PRODUCT_NAME := grapefruit_jp5s
PRODUCT_DEVICE := jp5s
PRODUCT_BRAND := Grapefruit
PRODUCT_MODEL := JP5S
PRODUCT_MANUFACTURER := JPay
PRODUCT_CHARACTERISTICS := tablet
PRODUCT_PACKAGE_OVERLAYS := device/grapefruit/jp5s/overlay
PRODUCT_PACKAGES += Settings SystemUI GrapefruitLauncher GrapefruitKeyboard GrapefruitCalculator GrapefruitMaps GrapefruitMedia
# Separate signed app prebuilts, with preserved certificates and original package IDs.
$(call inherit-product-if-exists, vendor/grapefruit/apps/apps.mk)
# Populated only by scripts/stage_vendor.py into a private, external AOSP workspace.
$(call inherit-product-if-exists, vendor/jpay/jp5s/jp5s-vendor.mk)
PRODUCT_COPY_FILES += \
    vendor/grapefruit/fonts/Geist-Regular.ttf:system/fonts/Geist-Regular.ttf \
    vendor/grapefruit/fonts/Geist-Medium.ttf:system/fonts/Geist-Medium.ttf \
    vendor/grapefruit/fonts/Geist-SemiBold.ttf:system/fonts/Geist-SemiBold.ttf \
    vendor/grapefruit/fonts/Geist-Bold.ttf:system/fonts/Geist-Bold.ttf \
    vendor/grapefruit/fonts/Geist-OFL.txt:system/fonts/Geist-OFL.txt
# Device density, EGL selection, audio policy and Wi-Fi properties await the working BSP.
