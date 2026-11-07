# Firmware geometry, ramdisk and kernel are deliberately not guessed.
TARGET_ARCH := arm
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_VARIANT := cortex-a9
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_BOARD_PLATFORM := rk3188
TARGET_BOOTLOADER_BOARD_NAME := rk30board
TARGET_NO_BOOTLOADER := true
TARGET_NO_RADIOIMAGE := true
TARGET_NO_KERNEL := true
USE_OPENGL_RENDERER := true
# No partition sizes, fstab, boot offsets or boot image recipes until independently verified.
