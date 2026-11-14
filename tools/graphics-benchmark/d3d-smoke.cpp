// 無料の合成ベンチマーク。D3D9/11 の単純な描画と結果確認。
#define COBJMACROS
#include <windows.h>
#include <d3d9.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define CHECK(expr) do { HRESULT hr_ = (expr); if (FAILED(hr_)) { std::fprintf(stderr, "%s: 0x%08lx\n", #expr, (unsigned long)hr_); return 1; } } while (0)
static const unsigned W = 320, H = 240, WARMUP = 30, FRAMES = 180;
static double seconds(LARGE_INTEGER begin, LARGE_INTEGER end) { LARGE_INTEGER freq; QueryPerformanceFrequency(&freq); return double(end.QuadPart - begin.QuadPart) / double(freq.QuadPart); }
static bool pixel_ok(const unsigned char *p) { return p[2] > 230 && p[1] < 20 && p[0] < 20 && p[3] > 230; } // BGRA red
static HWND make_window() {
    WNDCLASSA wc = {}; wc.lpfnWndProc = DefWindowProcA; wc.hInstance = GetModuleHandleA(nullptr); wc.lpszClassName = "GNBench";
    RegisterClassA(&wc);
    HWND win = CreateWindowA(wc.lpszClassName, "GNBench", WS_OVERLAPPEDWINDOW, 0, 0, W + 32, H + 64, nullptr, nullptr, wc.hInstance, nullptr);
    if (win) { ShowWindow(win, SW_SHOW); UpdateWindow(win); }
    return win;
}
static int run9(HWND hwnd) {
    IDirect3D9 *d = Direct3DCreate9(D3D_SDK_VERSION); if (!d) return 1;
    D3DPRESENT_PARAMETERS pp = {}; pp.Windowed = TRUE; pp.SwapEffect = D3DSWAPEFFECT_DISCARD; pp.hDeviceWindow = hwnd;
    pp.BackBufferWidth = W; pp.BackBufferHeight = H; pp.BackBufferFormat = D3DFMT_A8R8G8B8; pp.PresentationInterval = D3DPRESENT_INTERVAL_IMMEDIATE;
    IDirect3DDevice9 *dev = nullptr;
    CHECK(d->CreateDevice(D3DADAPTER_DEFAULT, D3DDEVTYPE_HAL, hwnd, D3DCREATE_SOFTWARE_VERTEXPROCESSING, &pp, &dev));
    struct V { float x, y, z, rhw; DWORD color; };
    V triangle[] = {{0,0,0,1,0xffff0000}, {float(W),0,0,1,0xffff0000}, {float(W)/2,float(H),0,1,0xffff0000}};
    CHECK(dev->SetFVF(D3DFVF_XYZRHW | D3DFVF_DIFFUSE));
    IDirect3DSurface9 *back = nullptr, *readback = nullptr;
    CHECK(dev->GetBackBuffer(0,0,D3DBACKBUFFER_TYPE_MONO,&back));
    CHECK(dev->CreateOffscreenPlainSurface(W,H,D3DFMT_A8R8G8B8,D3DPOOL_SYSTEMMEM,&readback,nullptr));
    LARGE_INTEGER start{}, end{};
    for (unsigned i = 0; i < WARMUP + FRAMES; ++i) {
        if (i == WARMUP) QueryPerformanceCounter(&start);
        CHECK(dev->Clear(0,nullptr,D3DCLEAR_TARGET,D3DCOLOR_ARGB(255,0,0,0),1,0));
        CHECK(dev->BeginScene()); CHECK(dev->DrawPrimitiveUP(D3DPT_TRIANGLELIST,1,triangle,sizeof(V))); CHECK(dev->EndScene());
        if (i == WARMUP + FRAMES - 1) {
            CHECK(dev->GetRenderTargetData(back,readback));
            D3DLOCKED_RECT r{}; CHECK(readback->LockRect(&r,nullptr,D3DLOCK_READONLY));
            bool ok = pixel_ok(static_cast<unsigned char*>(r.pBits) + (H/3)*r.Pitch + (W/2)*4);
            CHECK(readback->UnlockRect()); if (!ok) { std::fprintf(stderr,"pixel mismatch d3d9\n"); return 2; }
        }
        CHECK(dev->Present(nullptr,nullptr,nullptr,nullptr));
    }
    QueryPerformanceCounter(&end); double sec=seconds(start,end);
    std::printf("RESULT api=d3d9 frames=%u seconds=%.6f fps=%.3f pixel=ok\n",FRAMES,sec,FRAMES/sec);
    readback->Release(); back->Release(); dev->Release(); d->Release(); return 0;
}
static int run11(HWND hwnd) {
    DXGI_SWAP_CHAIN_DESC sd{}; sd.BufferDesc.Width=W; sd.BufferDesc.Height=H; sd.BufferDesc.Format=DXGI_FORMAT_B8G8R8A8_UNORM;
    sd.SampleDesc.Count=1; sd.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT; sd.BufferCount=1; sd.OutputWindow=hwnd; sd.Windowed=TRUE; sd.SwapEffect=DXGI_SWAP_EFFECT_DISCARD;
    ID3D11Device *dev=nullptr; ID3D11DeviceContext *ctx=nullptr; IDXGISwapChain *swap=nullptr; D3D_FEATURE_LEVEL fl{};
    CHECK(D3D11CreateDeviceAndSwapChain(nullptr,D3D_DRIVER_TYPE_HARDWARE,nullptr,0,nullptr,0,D3D11_SDK_VERSION,&sd,&swap,&dev,&fl,&ctx));
    ID3D11Texture2D *back=nullptr; CHECK(swap->GetBuffer(0,__uuidof(ID3D11Texture2D),(void**)&back));
    ID3D11RenderTargetView *rt=nullptr; CHECK(dev->CreateRenderTargetView(back,nullptr,&rt));
    D3D11_TEXTURE2D_DESC desc{}; back->GetDesc(&desc); desc.Usage=D3D11_USAGE_STAGING; desc.BindFlags=0; desc.CPUAccessFlags=D3D11_CPU_ACCESS_READ; desc.MiscFlags=0;
    ID3D11Texture2D *readback=nullptr; CHECK(dev->CreateTexture2D(&desc,nullptr,&readback));
    const char *shader="float4 vs(float4 p:POSITION):SV_Position { return p; } float4 ps():SV_Target { return float4(1,0,0,1); }";
    ID3DBlob *vsb=nullptr,*psb=nullptr,*errors=nullptr;
    HRESULT hr=D3DCompile(shader,strlen(shader),nullptr,nullptr,nullptr,"vs","vs_4_0",0,0,&vsb,&errors);
    if (FAILED(hr)) { if(errors) std::fprintf(stderr,"shader: %s\n",(char*)errors->GetBufferPointer()); return 1; } if(errors) { errors->Release(); errors=nullptr; }
    hr=D3DCompile(shader,strlen(shader),nullptr,nullptr,nullptr,"ps","ps_4_0",0,0,&psb,&errors);
    if (FAILED(hr)) { if(errors) std::fprintf(stderr,"shader: %s\n",(char*)errors->GetBufferPointer()); return 1; } if(errors) errors->Release();
    ID3D11VertexShader *vs=nullptr; ID3D11PixelShader *ps=nullptr;
    CHECK(dev->CreateVertexShader(vsb->GetBufferPointer(),vsb->GetBufferSize(),nullptr,&vs));
    CHECK(dev->CreatePixelShader(psb->GetBufferPointer(),psb->GetBufferSize(),nullptr,&ps));
    D3D11_INPUT_ELEMENT_DESC element={"POSITION",0,DXGI_FORMAT_R32G32B32A32_FLOAT,0,0,D3D11_INPUT_PER_VERTEX_DATA,0};
    ID3D11InputLayout *layout=nullptr; CHECK(dev->CreateInputLayout(&element,1,vsb->GetBufferPointer(),vsb->GetBufferSize(),&layout));
    const float vertices[12]={-1,1,0,1, 1,1,0,1, 0,-1,0,1};
    D3D11_BUFFER_DESC bd{}; bd.ByteWidth=sizeof(vertices); bd.Usage=D3D11_USAGE_IMMUTABLE; bd.BindFlags=D3D11_BIND_VERTEX_BUFFER;
    D3D11_SUBRESOURCE_DATA data{}; data.pSysMem=vertices; ID3D11Buffer *vb=nullptr; CHECK(dev->CreateBuffer(&bd,&data,&vb));
    UINT stride=4*sizeof(float),offset=0; ctx->IASetInputLayout(layout); ctx->IASetVertexBuffers(0,1,&vb,&stride,&offset);
    D3D11_VIEWPORT vp{}; vp.Width=float(W); vp.Height=float(H); vp.MaxDepth=1.0f;
    ctx->RSSetViewports(1,&vp); ctx->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    ctx->VSSetShader(vs,nullptr,0); ctx->PSSetShader(ps,nullptr,0);
    LARGE_INTEGER start{},end{}; const float black[4]={0,0,0,1};
    for (unsigned i=0;i<WARMUP+FRAMES;++i) {
        if(i==WARMUP) QueryPerformanceCounter(&start);
        ctx->OMSetRenderTargets(1,&rt,nullptr); ctx->ClearRenderTargetView(rt,black); ctx->Draw(3,0);
        if(i==WARMUP+FRAMES-1) {
            ctx->CopyResource(readback,back); D3D11_MAPPED_SUBRESOURCE m{}; CHECK(ctx->Map(readback,0,D3D11_MAP_READ,0,&m));
            bool ok=pixel_ok(static_cast<unsigned char*>(m.pData)+(H/3)*m.RowPitch+(W/2)*4); ctx->Unmap(readback,0);
            if(!ok) { std::fprintf(stderr,"pixel mismatch d3d11\n"); return 2; }
        }
        CHECK(swap->Present(0,0));
    }
    QueryPerformanceCounter(&end); double sec=seconds(start,end);
    std::printf("RESULT api=d3d11 frames=%u seconds=%.6f fps=%.3f pixel=ok feature=0x%x\n",FRAMES,sec,FRAMES/sec,unsigned(fl));
    vb->Release(); layout->Release(); ps->Release(); vs->Release(); psb->Release(); vsb->Release(); readback->Release(); rt->Release(); back->Release(); swap->Release(); ctx->Release(); dev->Release(); return 0;
}
int main(int argc,char **argv) { if(argc!=2 || (strcmp(argv[1],"d3d9") && strcmp(argv[1],"d3d11"))) return 2; HWND hwnd=make_window(); if(!hwnd) return 1; int ret=strcmp(argv[1],"d3d9") ? run11(hwnd):run9(hwnd); DestroyWindow(hwnd); return ret; }
