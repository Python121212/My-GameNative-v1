// SPDX-License-Identifier: GPL-3.0-or-later
#include "texture9.hpp"
#include <cstdint>
#include <cstdio>
#include <exception>
#include <stdexcept>
#include <vector>
using gn::d3d9::UploadContext;
using gn::d3d9::Texture9;
static void require(bool ok,const char* what) { if (!ok) throw std::runtime_error(what); }
template<class E, class F> static void throws(F f) { try { f(); } catch(const E&) { return; } throw std::runtime_error("期待した例外がありません"); }
static void set(Texture9::LockedRect l, unsigned x,unsigned y,uint8_t b,uint8_t g,uint8_t r,uint8_t a=255) {
    auto* p=static_cast<uint8_t*>(l.bits)+size_t(y)*l.pitch+x*4; p[0]=b;p[1]=g;p[2]=r;p[3]=a;
}
static void pixel(const std::vector<uint8_t>& a,unsigned w,unsigned x,unsigned y,uint8_t b,uint8_t g,uint8_t r,uint8_t alpha=255) {
    auto i=(size_t(y)*w+x)*4;
    require(a.at(i)==b && a.at(i+1)==g && a.at(i+2)==r && a.at(i+3)==alpha,"BGRA ピクセルが一致しません");
}
int main() {
 try {
    // 4ピクセル分のみ: 横方向と縦方向の両方に分割される。
    UploadContext context(16);
    throws<std::invalid_argument>([&]{Texture9 z(context,0,2,false);});
    Texture9 t(context,7,4,true), other(context,2,2,false);
    auto initial=t.readback();
    for (uint8_t byte:initial) require(byte==0,"初期画像がゼロではありません");
    throws<std::logic_error>([&]{t.unlockRect();});
    throws<std::invalid_argument>([&]{t.lockRect(Texture9::Rect{7,0,1,1});});
    throws<std::invalid_argument>([&]{t.lockRect(Texture9::Rect{UINT32_MAX,0,1,1});});
    throws<std::invalid_argument>([&]{t.lockRect(std::nullopt,0x80);});
    throws<std::invalid_argument>([&]{other.lockRect(std::nullopt,Texture9::Discard);});
    throws<std::invalid_argument>([&]{t.lockRect(Texture9::Rect{0,0,7,4},Texture9::Discard);});
    throws<std::invalid_argument>([&]{t.lockRect(std::nullopt,Texture9::ReadOnly|Texture9::Discard);});
    auto whole=t.lockRect(); require(whole.pitch==28,"全画像のピッチが不正です");
    throws<std::logic_error>([&]{t.lockRect();});
    throws<std::logic_error>([&]{t.flush();});
    throws<std::logic_error>([&]{t.readback();});
    for (unsigned y=0;y<4;y++) for(unsigned x=0;x<7;x++) set(whole,x,y,uint8_t(x),uint8_t(y),31);
    t.unlockRect(); t.flush();
    // 他のテクスチャとの保留済み更新が混在しても順序を守る。
    auto o=other.lockRect(); for(unsigned y=0;y<2;y++) for(unsigned x=0;x<2;x++) set(o,x,y,9,8,7); other.unlockRect(); other.flush();
    auto partial=t.lockRect(Texture9::Rect{2,1,3,2}); require(partial.pitch==28,"部分矩形のピッチが不正です");
    auto* existing=static_cast<uint8_t*>(partial.bits); require(existing[0]==2 && existing[1]==1 && existing[2]==31,"以前のデータが保持されていません");
    set(partial,0,0,55,66,77); set(partial,2,1,88,99,111); t.unlockRect();
    auto result=t.readback(); pixel(result,7,2,1,55,66,77); pixel(result,7,4,2,88,99,111); pixel(result,7,6,3,6,3,31); pixel(other.readback(),2,1,1,9,8,7);
    // READONLY は shadow を変更しない限り再転送しない。
    auto ro=t.lockRect(Texture9::Rect{2,1,1,1},Texture9::ReadOnly); require(static_cast<uint8_t*>(ro.bits)[0]==55,"読み取り専用ロックが不正"); t.unlockRect();
    pixel(t.readback(),7,2,1,55,66,77);
    auto discard=t.lockRect(std::nullopt,Texture9::Discard); set(discard,6,3,17,18,19); t.unlockRect();
    result=t.readback(); pixel(result,7,6,3,17,18,19); pixel(result,7,2,1,0,0,0,0);
    for(unsigned i=0;i<10;i++) {
        auto l=t.lockRect(Texture9::Rect{i%7,i%4,1,1}); set(l,0,0,uint8_t(i),21,42); t.unlockRect(); t.flush();
        result=t.readback(); pixel(result,7,i%7,i%4,uint8_t(i),21,42);
    }
    std::puts("D3D9 型ロック・矩形分割・Vulkan 転送テスト成功"); return 0;
 } catch(const std::exception& e) { std::fprintf(stderr,"失敗: %s\n",e.what()); return 1; }
}
