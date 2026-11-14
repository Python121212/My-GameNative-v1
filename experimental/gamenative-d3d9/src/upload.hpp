// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <vulkan/vulkan.h>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace gn::d3d9 {
// Vulkan のオフスクリーン転送基盤。D3D9 ABI、描画、表示はまだ実装していない。
// 単一スレッド専用。Vulkan エラー時はインスタンスを破棄して作り直す。
class UploadContext {
public:
    using TextureId = std::uint32_t;
    // little-endian の D3DFMT_A8R8G8B8 は B,G,R,A の順。
    enum class Format { Rgba8, Bgra8 };
    struct Rect { std::uint32_t x, y, width, height; };
    struct Lock { void* pixels; std::uint32_t pitch; };
    explicit UploadContext(VkDeviceSize stagingBytes = 8 * 1024 * 1024);
    ~UploadContext();
    UploadContext(const UploadContext&) = delete;
    UploadContext& operator=(const UploadContext&) = delete;
    TextureId createTexture(std::uint32_t width, std::uint32_t height, Format format = Format::Rgba8);
    Lock lock(TextureId id, Rect rect);
    void unlock();
    // 記録済みの更新を一回のキュー送信にまとめる。GPU の完了は待たない。
    void submit();
    // テスト用同期読み戻し。テクスチャのバイト順のまま上から返す。
    std::vector<std::uint8_t> readback(TextureId id);
    void wait();
    VkDeviceSize stagingCapacity() const;
private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};
}
