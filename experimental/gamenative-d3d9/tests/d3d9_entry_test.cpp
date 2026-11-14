// SPDX-License-Identifier: GPL-3.0-or-later
#include <windows.h>
#include <d3d9.h>
#include <cstdio>
#include <cstdlib>
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr,"失敗: %s 行 %d\n",#x,__LINE__); std::exit(1); } } while (0)
int main() {
    HMODULE dll=LoadLibraryA("gn-d3d9-experimental.dll"); CHECK(dll);
    using Create9=IDirect3D9* (WINAPI*)(UINT);
    using Create9Ex=HRESULT (WINAPI*)(UINT,IDirect3D9Ex**);
    auto create=reinterpret_cast<Create9>(GetProcAddress(dll,"Direct3DCreate9"));
    auto createEx=reinterpret_cast<Create9Ex>(GetProcAddress(dll,"Direct3DCreate9Ex"));
    CHECK(create && createEx); CHECK(create(0)==nullptr);
    IDirect3D9Ex* ex=reinterpret_cast<IDirect3D9Ex*>(1);
    CHECK(createEx(D3D_SDK_VERSION,&ex)==D3DERR_NOTAVAILABLE && ex==nullptr);
    CHECK(createEx(D3D_SDK_VERSION,nullptr)==D3DERR_INVALIDCALL);
    IDirect3D9* d=create(D3D_SDK_VERSION); CHECK(d); CHECK(d->GetAdapterCount()==0);
    CHECK(d->QueryInterface(IID_IUnknown,nullptr)==E_POINTER);
    void* unknown=nullptr; CHECK(d->QueryInterface(IID_IUnknown,&unknown)==S_OK && unknown==d);
    static_cast<IUnknown*>(unknown)->Release();
    void* same=nullptr; CHECK(d->QueryInterface(IID_IDirect3D9,&same)==S_OK && same==d);
    static_cast<IDirect3D9*>(same)->Release();
    void* missing=reinterpret_cast<void*>(1); CHECK(d->QueryInterface(IID_IDirect3D9Ex,&missing)==E_NOINTERFACE && missing==nullptr);
    IDirect3DDevice9* dev=reinterpret_cast<IDirect3DDevice9*>(1);
    CHECK(d->CreateDevice(0,D3DDEVTYPE_HAL,nullptr,0,nullptr,&dev)==D3DERR_NOTAVAILABLE && !dev);
    DWORD quality=123; CHECK(d->CheckDeviceMultiSampleType(0,D3DDEVTYPE_HAL,D3DFMT_A8R8G8B8,TRUE,D3DMULTISAMPLE_NONE,&quality)==D3DERR_INVALIDCALL && !quality);
    CHECK(d->AddRef()==2); CHECK(d->Release()==1); CHECK(d->Release()==0);
    CHECK(FreeLibrary(dll)); std::puts("D3D9 DLL 入口と未実装の安全な失敗を確認"); return 0;
}
