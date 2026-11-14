// SPDX-License-Identifier: GPL-3.0-or-later
#include "texture9.hpp"
#include <algorithm>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace gn::d3d9 {
namespace {
std::size_t imageBytes(std::uint32_t w, std::uint32_t h) {
    if (!w || !h || w > UINT32_MAX/4) throw std::invalid_argument("画像サイズが不正です");
    std::uint64_t bytes = std::uint64_t(w)*4*h;
    if (bytes > std::numeric_limits<std::size_t>::max()) throw std::length_error("画像が大きすぎます");
    return static_cast<std::size_t>(bytes);
}
}
Texture9::Texture9(UploadContext& context, std::uint32_t w, std::uint32_t h, bool dynamic)
    : context_(context), width_(w), height_(h), pitch_(w <= UINT32_MAX/4 ? w*4 : 0), dynamic_(dynamic),
      dirtyRect_{0,0,w,h}, shadow_(imageBytes(w,h), 0), texture_(context.createTexture(w,h,UploadContext::Format::Bgra8)) {}

Texture9::LockedRect Texture9::lockRect(std::optional<Rect> rect, std::uint32_t flags) {
    if (locked_) throw std::logic_error("テクスチャはロック中です");
    if (flags & ~(ReadOnly | Discard)) throw std::invalid_argument("未対応のロックフラグです");
    if ((flags & Discard) && (!dynamic_ || rect.has_value() || (flags & ReadOnly)))
        throw std::invalid_argument("DISCARD は動的テクスチャ全体の書き込みにのみ使えます");
    Rect r=rect.value_or(Rect{0,0,width_,height_});
    if (!r.width || !r.height || r.x>width_ || r.y>height_ || r.width>width_-r.x || r.height>height_-r.y)
        throw std::invalid_argument("ロック矩形が不正です");
    // DISCARD の古い内容は D3D9 では未定義。実験版では安全のためゼロ初期化。
    if (flags & Discard) std::fill(shadow_.begin(),shadow_.end(),0);
    locked_=true; readOnly_=(flags & ReadOnly)!=0; lockedRect_=r;
    return {shadow_.data()+std::size_t(r.y)*pitch_+std::size_t(r.x)*4,pitch_};
}
void Texture9::markDirty(Rect r) {
    if (!dirty_) { dirtyRect_=r; dirty_=true; return; }
    const auto right=std::max(dirtyRect_.x+dirtyRect_.width,r.x+r.width);
    const auto bottom=std::max(dirtyRect_.y+dirtyRect_.height,r.y+r.height);
    dirtyRect_.x=std::min(dirtyRect_.x,r.x); dirtyRect_.y=std::min(dirtyRect_.y,r.y);
    dirtyRect_.width=right-dirtyRect_.x; dirtyRect_.height=bottom-dirtyRect_.y;
}
void Texture9::unlockRect() {
    if (!locked_) throw std::logic_error("テクスチャがロックされていません");
    if (!readOnly_) markDirty(lockedRect_);
    locked_=false;
}
void Texture9::flush() {
    if (locked_) throw std::logic_error("ロック中は転送できません");
    if (!dirty_) return;
    // 1行も収まらない小さな転送領域でも、水平／垂直方向に分割できる。
    const auto maxPixels=context_.stagingCapacity()/4;
    for (std::uint32_t x=dirtyRect_.x; x<dirtyRect_.x+dirtyRect_.width;) {
        const auto w=static_cast<std::uint32_t>(std::min<std::uint64_t>(dirtyRect_.x+dirtyRect_.width-x,maxPixels));
        const auto maxRows=std::max<std::uint64_t>(1,maxPixels/w);
        for (std::uint32_t y=dirtyRect_.y; y<dirtyRect_.y+dirtyRect_.height;) {
            const auto h=static_cast<std::uint32_t>(std::min<std::uint64_t>(dirtyRect_.y+dirtyRect_.height-y,maxRows));
            Rect rect{x,y,w,h}; UploadContext::Lock dst;
            try { dst=context_.lock(texture_,rect); }
            catch (const std::length_error&) { context_.submit(); dst=context_.lock(texture_,rect); }
            for (std::uint32_t row=0;row<h;++row)
                std::memcpy(static_cast<std::uint8_t*>(dst.pixels)+std::size_t(row)*dst.pitch,
                            shadow_.data()+std::size_t(y+row)*pitch_+std::size_t(x)*4,std::size_t(w)*4);
            context_.unlock(); y+=h;
        }
        x+=w;
    }
    dirty_=false;
}
std::vector<std::uint8_t> Texture9::readback() { flush(); return context_.readback(texture_); }
}
