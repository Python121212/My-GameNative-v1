// SPDX-License-Identifier: GPL-3.0-or-later
// 実験用 D3D9 ABI の入口。デバイスや描画機能はまだ実装しない。
#include <windows.h>
#include <d3d9.h>
#include <new>

namespace {
class Direct3D9 final : public IDirect3D9 {
    volatile LONG references_ = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID iid, void** out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        if (IsEqualIID(iid, IID_IUnknown) || IsEqualIID(iid, IID_IDirect3D9)) {
            *out = static_cast<IDirect3D9*>(this);
            AddRef();
            return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return static_cast<ULONG>(InterlockedIncrement(&references_)); }
    ULONG STDMETHODCALLTYPE Release() override {
        const auto remaining=InterlockedDecrement(&references_);
        if (!remaining) delete this;
        return static_cast<ULONG>(remaining);
    }
    HRESULT STDMETHODCALLTYPE RegisterSoftwareDevice(void*) override { return D3DERR_INVALIDCALL; }
    UINT STDMETHODCALLTYPE GetAdapterCount() override { return 0; }
    HRESULT STDMETHODCALLTYPE GetAdapterIdentifier(UINT,DWORD,D3DADAPTER_IDENTIFIER9*) override { return D3DERR_INVALIDCALL; }
    UINT STDMETHODCALLTYPE GetAdapterModeCount(UINT,D3DFORMAT) override { return 0; }
    HRESULT STDMETHODCALLTYPE EnumAdapterModes(UINT,D3DFORMAT,UINT,D3DDISPLAYMODE*) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE GetAdapterDisplayMode(UINT,D3DDISPLAYMODE*) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE CheckDeviceType(UINT,D3DDEVTYPE,D3DFORMAT,D3DFORMAT,BOOL) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE CheckDeviceFormat(UINT,D3DDEVTYPE,D3DFORMAT,DWORD,D3DRESOURCETYPE,D3DFORMAT) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE CheckDeviceMultiSampleType(UINT,D3DDEVTYPE,D3DFORMAT,BOOL,D3DMULTISAMPLE_TYPE,DWORD* quality) override {
        if (quality) *quality=0;
        return D3DERR_INVALIDCALL;
    }
    HRESULT STDMETHODCALLTYPE CheckDepthStencilMatch(UINT,D3DDEVTYPE,D3DFORMAT,D3DFORMAT,D3DFORMAT) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE CheckDeviceFormatConversion(UINT,D3DDEVTYPE,D3DFORMAT,D3DFORMAT) override { return D3DERR_INVALIDCALL; }
    HRESULT STDMETHODCALLTYPE GetDeviceCaps(UINT,D3DDEVTYPE,D3DCAPS9*) override { return D3DERR_INVALIDCALL; }
    HMONITOR STDMETHODCALLTYPE GetAdapterMonitor(UINT) override { return nullptr; }
    HRESULT STDMETHODCALLTYPE CreateDevice(UINT,D3DDEVTYPE,HWND,DWORD,D3DPRESENT_PARAMETERS*,IDirect3DDevice9** out) override {
        if (out) *out=nullptr;
        return D3DERR_NOTAVAILABLE;
    }
};
}
extern "C" IDirect3D9* WINAPI Direct3DCreate9(UINT version) {
    if (version != D3D_SDK_VERSION) return nullptr;
    return new (std::nothrow) Direct3D9();
}
extern "C" HRESULT WINAPI Direct3DCreate9Ex(UINT version, IDirect3D9Ex** out) {
    if (!out) return D3DERR_INVALIDCALL;
    *out=nullptr;
    if (version != D3D_SDK_VERSION) return D3DERR_INVALIDCALL;
    return D3DERR_NOTAVAILABLE;
}
