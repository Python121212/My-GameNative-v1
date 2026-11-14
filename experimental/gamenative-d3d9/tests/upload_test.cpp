// SPDX-License-Identifier: GPL-3.0-or-later
#include "upload.hpp"
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <exception>
#include <stdexcept>
#include <vector>
using gn::d3d9::UploadContext;
static void require(bool v, const char* message) { if (!v) throw std::runtime_error(message); }
template<class E, class F> static void throws(F f) { try { f(); } catch (const E&) { return; } throw std::runtime_error("期待した例外がありません"); }
static void fill(UploadContext& ctx, UploadContext::TextureId id, UploadContext::Rect rect, uint8_t r, uint8_t g, uint8_t b) {
    auto lock=ctx.lock(id,rect); require(lock.pitch==rect.width*4,"ピッチが不正");
    for (uint32_t y=0;y<rect.height;y++) for(uint32_t x=0;x<rect.width;x++) {
        auto* p=static_cast<uint8_t*>(lock.pixels)+y*lock.pitch+x*4; p[0]=r; p[1]=g; p[2]=b; p[3]=255;
    }
    ctx.unlock();
}
static void pixel(const std::vector<uint8_t>& data,uint32_t width,uint32_t x,uint32_t y,uint8_t r,uint8_t g,uint8_t b) {
    auto off=(size_t(y)*width+x)*4; require(data.at(off)==r && data.at(off+1)==g && data.at(off+2)==b && data.at(off+3)==255,"ピクセル不一致");
}
int main() {
 try {
    UploadContext ctx(512);
    throws<std::invalid_argument>([&]{ctx.createTexture(0,4);});
    auto a=ctx.createTexture(8,8), b=ctx.createTexture(4,4), uninitialized=ctx.createTexture(1,1);
    throws<std::logic_error>([&]{ctx.readback(uninitialized);});
    throws<std::invalid_argument>([&]{ctx.lock(99,{0,0,1,1});});
    throws<std::invalid_argument>([&]{ctx.lock(a,{7,0,2,1});});
    throws<std::invalid_argument>([&]{ctx.lock(a,{UINT32_MAX,0,1,1});});
    throws<std::logic_error>([&]{ctx.unlock();});
    auto lk=ctx.lock(a,{0,0,8,8});
    throws<std::logic_error>([&]{ctx.lock(a,{0,0,1,1});});
    throws<std::logic_error>([&]{ctx.submit();});
    for(unsigned i=0;i<8*8;i++) { auto* p=static_cast<uint8_t*>(lk.pixels)+i*4; p[0]=11;p[1]=22;p[2]=33;p[3]=255; }
    ctx.unlock();
    fill(ctx,b,{0,0,4,4},90,91,92);
    fill(ctx,a,{2,2,3,3},40,50,60);
    fill(ctx,a,{3,3,2,2},70,80,90);
    ctx.submit();
    auto ar=ctx.readback(a), br=ctx.readback(b);
    pixel(ar,8,0,0,11,22,33); pixel(ar,8,2,2,40,50,60); pixel(ar,8,3,3,70,80,90); pixel(ar,8,7,7,11,22,33); pixel(br,4,3,3,90,91,92);
    // 複数フレームで読み戻し後のレイアウトとフェンスの再利用を確認。
    for(unsigned i=0;i<8;i++) {
        fill(ctx,a,{0,0,8,8},uint8_t(i),23,42); ctx.submit();
        // 前回の転送完了を待って同じステージング領域を再使用。
        fill(ctx,a,{0,0,1,1},uint8_t(i+16),23,42); ctx.submit();
        auto frame=ctx.readback(a); pixel(frame,8,0,0,uint8_t(i+16),23,42); pixel(frame,8,7,7,uint8_t(i),23,42);
    }
    fill(ctx,a,{0,0,8,8},1,2,3); fill(ctx,a,{0,0,8,8},4,5,6);
    throws<std::length_error>([&]{ctx.lock(a,{0,0,1,1});});
    ctx.submit(); ctx.wait(); fill(ctx,a,{0,0,1,1},7,8,9);
    ar=ctx.readback(a); pixel(ar,8,0,0,7,8,9); pixel(ar,8,7,7,4,5,6);
    std::puts("Vulkan 転送・部分更新・同期・検査成功"); return 0;
 } catch (const std::exception& e) { std::fprintf(stderr,"失敗: %s\n",e.what()); return 1; }
}
