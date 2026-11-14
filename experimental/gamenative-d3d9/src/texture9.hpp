// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include "upload.hpp"
#include <cstdint>
#include <optional>
#include <vector>

namespace gn::d3d9 {
// D3D9 の一部の LockRect 振る舞いを持つ実験用フロントエンド。
// COM や HRESULT インターフェースではない。コンテキストは本オブジェクトより長生きすること。
class Texture9 {
public:
    static constexpr std::uint32_t ReadOnly = 0x0010;  // D3DLOCK_READONLY
    static constexpr std::uint32_t Discard = 0x2000;   // D3DLOCK_DISCARD
    using Rect = UploadContext::Rect;
    struct LockedRect { void* bits; std::uint32_t pitch; };
    Texture9(UploadContext& context, std::uint32_t width, std::uint32_t height, bool dynamic);
    Texture9(const Texture9&) = delete;
    Texture9& operator=(const Texture9&) = delete;
    // rect が省略された場合は画像全体。未対応フラグは拒否する。
    LockedRect lockRect(std::optional<Rect> rect = std::nullopt, std::uint32_t flags = 0);
    void unlockRect();
    // CPU 側の更新を Vulkan 転送キューに追加。GPU 完了を待たない。
    void flush();
    // 試験用。送信完了後の BGRA8 を読み戻す。
    std::vector<std::uint8_t> readback();
    UploadContext::TextureId id() const { return texture_; }
private:
    UploadContext& context_;
    std::uint32_t width_, height_, pitch_;
    bool dynamic_, locked_ = false, readOnly_ = false, dirty_ = true;
    Rect lockedRect_{}, dirtyRect_{};
    std::vector<std::uint8_t> shadow_;
    UploadContext::TextureId texture_;
    void markDirty(Rect rect);
};
}
