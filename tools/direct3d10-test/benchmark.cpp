// SPDX-License-Identifier: GPL-3.0-or-later
// Direct3D 10 合成負荷。計測区間外に描画検証し、描画完了をイベントクエリで待つ。
#include <windows.h>
#include <d3d10.h>
#include <d3dcompiler.h>
#include <cstdio>
#include <cstring>

#define CHECK(call) do { HRESULT result_=(call); if (FAILED(result_)) { std::fprintf(stderr, "%s: HRESULT=0x%08lx\n", #call, static_cast<unsigned long>(result_)); return 1; } } while (0)
static constexpr UINT WIDTH=320, HEIGHT=240;
static HWND window() {
    WNDCLASSA wc{}; wc.lpfnWndProc=DefWindowProcA; wc.hInstance=GetModuleHandleA(nullptr); wc.lpszClassName="GN_D3D10_Test";
    if (!RegisterClassA(&wc)) return nullptr;
    RECT rect{0,0,static_cast<LONG>(WIDTH),static_cast<LONG>(HEIGHT)};
    if (!AdjustWindowRect(&rect,WS_OVERLAPPEDWINDOW,FALSE)) return nullptr;
    HWND w=CreateWindowA(wc.lpszClassName,"Direct3D 10 test",WS_OVERLAPPEDWINDOW,0,0,rect.right-rect.left,rect.bottom-rect.top,nullptr,nullptr,wc.hInstance,nullptr);
    if (w) { ShowWindow(w,SW_SHOW); UpdateWindow(w); } return w;
}
static bool drain(ID3D10Device* dev, ID3D10Query* query) {
    query->End(); dev->Flush();
    BOOL complete=FALSE; ULONGLONG deadline=GetTickCount64()+30000;
    for (;;) {
        HRESULT status=query->GetData(&complete,sizeof(complete),0);
        if(status==S_OK && complete) return true;
        if(FAILED(status) || GetTickCount64()>=deadline) { std::fprintf(stderr,"GPU イベント完了待ち失敗 status=0x%08lx\n",static_cast<unsigned long>(status)); return false; }
        Sleep(0);
    }
}
int main() {
    HWND hwnd=window(); if (!hwnd) { std::fprintf(stderr,"ウィンドウ作成失敗\n"); return 1; }
    DXGI_SWAP_CHAIN_DESC sd{}; sd.BufferDesc.Width=WIDTH; sd.BufferDesc.Height=HEIGHT; sd.BufferDesc.Format=DXGI_FORMAT_B8G8R8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator=60; sd.BufferDesc.RefreshRate.Denominator=1;
    sd.SampleDesc.Count=1; sd.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT; sd.BufferCount=1; sd.OutputWindow=hwnd; sd.Windowed=TRUE; sd.SwapEffect=DXGI_SWAP_EFFECT_DISCARD;
    ID3D10Device* dev=nullptr; IDXGISwapChain* swap=nullptr;
    CHECK(D3D10CreateDeviceAndSwapChain(nullptr,D3D10_DRIVER_TYPE_HARDWARE,nullptr,0,D3D10_SDK_VERSION,&sd,&swap,&dev));
    ID3D10Texture2D* back=nullptr; CHECK(swap->GetBuffer(0,__uuidof(ID3D10Texture2D),reinterpret_cast<void**>(&back)));
    ID3D10RenderTargetView* rt=nullptr; CHECK(dev->CreateRenderTargetView(back,nullptr,&rt));
    D3D10_TEXTURE2D_DESC desc{}; back->GetDesc(&desc); desc.Usage=D3D10_USAGE_STAGING; desc.BindFlags=0; desc.CPUAccessFlags=D3D10_CPU_ACCESS_READ; desc.MiscFlags=0;
    ID3D10Texture2D* cpu=nullptr; CHECK(dev->CreateTexture2D(&desc,nullptr,&cpu));
    const char* shader="float4 vs(float4 p:POSITION):SV_Position { return p; } float4 ps():SV_Target { return float4(1,0,0,1); }";
    ID3DBlob *vsb=nullptr,*psb=nullptr,*errors=nullptr;
    HRESULT hr=D3DCompile(shader,std::strlen(shader),nullptr,nullptr,nullptr,"vs","vs_4_0",0,0,&vsb,&errors);
    if (FAILED(hr)) { if (errors) std::fprintf(stderr,"vertex shader: %s\n",static_cast<char*>(errors->GetBufferPointer())); return 1; }
    if(errors) { errors->Release(); errors=nullptr; }
    hr=D3DCompile(shader,std::strlen(shader),nullptr,nullptr,nullptr,"ps","ps_4_0",0,0,&psb,&errors);
    if (FAILED(hr)) { if (errors) std::fprintf(stderr,"pixel shader: %s\n",static_cast<char*>(errors->GetBufferPointer())); return 1; }
    if(errors) errors->Release();
    ID3D10VertexShader* vs=nullptr; ID3D10PixelShader* ps=nullptr;
    CHECK(dev->CreateVertexShader(vsb->GetBufferPointer(),vsb->GetBufferSize(),&vs));
    CHECK(dev->CreatePixelShader(psb->GetBufferPointer(),psb->GetBufferSize(),&ps));
    D3D10_INPUT_ELEMENT_DESC el={"POSITION",0,DXGI_FORMAT_R32G32B32A32_FLOAT,0,0,D3D10_INPUT_PER_VERTEX_DATA,0};
    ID3D10InputLayout* layout=nullptr; CHECK(dev->CreateInputLayout(&el,1,vsb->GetBufferPointer(),vsb->GetBufferSize(),&layout));
    const float vertices[]={-1,1,0,1, 1,1,0,1, 0,-1,0,1};
    D3D10_BUFFER_DESC bd{}; bd.ByteWidth=sizeof(vertices); bd.Usage=D3D10_USAGE_IMMUTABLE; bd.BindFlags=D3D10_BIND_VERTEX_BUFFER;
    D3D10_SUBRESOURCE_DATA data{}; data.pSysMem=vertices; ID3D10Buffer* vb=nullptr; CHECK(dev->CreateBuffer(&bd,&data,&vb));
    UINT stride=4*sizeof(float), offset=0;
    dev->IASetInputLayout(layout); dev->IASetVertexBuffers(0,1,&vb,&stride,&offset); dev->IASetPrimitiveTopology(D3D10_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    dev->VSSetShader(vs); dev->PSSetShader(ps);
    D3D10_VIEWPORT vp{}; vp.Width=WIDTH; vp.Height=HEIGHT; vp.MaxDepth=1.0f; dev->RSSetViewports(1,&vp);
    const float black[4]={0,0,0,1};
    D3D10_QUERY_DESC queryDesc{}; queryDesc.Query=D3D10_QUERY_EVENT;
    ID3D10Query* finished=nullptr; CHECK(dev->CreateQuery(&queryDesc,&finished));
    LARGE_INTEGER start{}, end{}, freq{}; if (!QueryPerformanceFrequency(&freq) || !freq.QuadPart) return 1;
    constexpr unsigned warmup=30, frames=300;
    for(unsigned frame=0;frame<warmup+frames+1;frame++) {
        if(frame==warmup) { if(!drain(dev,finished) || !QueryPerformanceCounter(&start)) return 1; }
        dev->OMSetRenderTargets(1,&rt,nullptr); dev->ClearRenderTargetView(rt,black); dev->Draw(3,0);
        // 最初と計測後の一枚は読み戻すが、計測区間には含めない。
        if(frame==0 || frame==warmup+frames) {
            dev->CopyResource(cpu,back); D3D10_MAPPED_TEXTURE2D mapped{}; CHECK(cpu->Map(0,D3D10_MAP_READ,0,&mapped));
            const auto* base=static_cast<const unsigned char*>(mapped.pData);
            const auto* red=base+(HEIGHT/3)*mapped.RowPitch+(WIDTH/2)*4;
            const auto* bg=base+(HEIGHT-2)*mapped.RowPitch+2*4;
            bool valid=red[0]<20 && red[1]<20 && red[2]>230 && red[3]>230 && bg[0]<20 && bg[1]<20 && bg[2]<20 && bg[3]>230;
            if (!valid) std::fprintf(stderr,"描画不一致 frame=%u 赤=%u,%u,%u,%u 黒=%u,%u,%u,%u\n",frame,red[0],red[1],red[2],red[3],bg[0],bg[1],bg[2],bg[3]);
            cpu->Unmap(0); if(!valid) return 2;
        }
        CHECK(swap->Present(0,0));
        MSG msg; while(PeekMessageA(&msg,nullptr,0,0,PM_REMOVE)) { TranslateMessage(&msg); DispatchMessageA(&msg); }
        if(frame==warmup+frames-1) {
            if (!drain(dev,finished)) return 1;
            if (!QueryPerformanceCounter(&end)) return 1;
        }
    }
    double seconds=double(end.QuadPart-start.QuadPart)/double(freq.QuadPart);
    if (!(seconds>0.0)) return 1;
    std::printf("RESULT d3d10 frames=%u seconds=%.6f fps=%.3f pixels=ok present=ok query=ok\n",frames,seconds,frames/seconds);
    finished->Release();
    vb->Release(); layout->Release(); ps->Release(); vs->Release(); psb->Release(); vsb->Release(); cpu->Release(); rt->Release(); back->Release(); swap->Release(); dev->Release(); DestroyWindow(hwnd);
    return 0;
}
