//src-tauri/src/lib.rs
/*
// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![greet])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
*/

use serde::{Deserialize, Serialize};
use std::thread;
use tauri::Listener;
use tauri::Manager;
use tauri::webview::PageLoadEvent;

#[cfg(target_os = "android")]
use tauri_plugin_android_fs::AndroidFsExt;

mod download;
mod js_eval;
mod api;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Mp3Data {
    mp3_url: String,
    mp3_name: String, // 新增
}

#[tauri::command]
fn greet(message: String) {
    println!("[前端]{}", message);
}
/*
fn inject_script(window: &tauri::WebviewWindow) {
    if let Ok(url) = window.url() {
        println!("[Rust] 执行 inject_script，URL: {}", url);
    }

    let _ = window.eval(js_eval::inject());
}
*/
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let t = tauri::Builder::default();
    #[cfg(target_os = "android")]
    let t = t.plugin(tauri_plugin_android_fs::init());
    t.setup(|app| {
        let main_window = app.get_webview_window("main").unwrap();
        let value = main_window.clone();
        #[cfg(target_os = "android")]
        let value2 = main_window.clone();

        // 检查并请求权限（Android 9 及以下需要，10+ 立即返回 true）
        #[cfg(target_os = "android")]
        tauri::async_runtime::spawn(async move {
            let _ = value2
                .app_handle()
                .android_fs_async()
                .public_storage()
                .request_permission()
                .await;
        });

        // 监听来自前端的 mp3_captured 事件
        main_window.listen("mp3_captured", move |event| {
            let payload = event.payload();
            if !payload.is_empty() {
                match serde_json::from_str::<Mp3Data>(payload) {
                    Ok(data) => {
                        println!("✅ 获取到 mp3_url: {}", data.mp3_url);
                        let window_for_download = value.clone();
                        thread::spawn(move || {
                            let app_handle = window_for_download.app_handle();
                            let m = download::download_file(
                                app_handle.clone(),
                                &data.mp3_url,
                                html_escape::decode_html_entities(&data.mp3_name)
                                    .to_string()
                                    .as_str(),
                            );
                            let m = m.as_str();
                            let _ = match m {
                                "Done" => window_for_download.eval(js_eval::cleanup_and_back()),
                                _ => window_for_download.eval(js_eval::show_error(m)),
                            };
                        });
                    }
                    Err(e) => eprintln!("⚠️ 解析失败: {:?}", e),
                }
            } else {
                println!("⚠️ 收到空 payload");
            }
        });

        // 启动后台线程，轮询 URL 变化并注入脚本
        /*
        let window_clone = main_window.clone();
        thread::spawn(move || {
            thread::sleep(Duration::from_secs(4)); // 等待 4 秒，确保页面加载完成
            let mut last_url = String::new();
            loop {
                // 获取当前 URL
                if let Ok(url) = window_clone.url() {
                    let url_str = url.to_string();
                    if url_str != last_url {
                        last_url = url_str;
                        // URL 发生变化，注入脚本
                        println!("[Rust] URL 变化，注入脚本，当前 URL: {}", last_url);
                        inject_script(&window_clone);
                    }
                }
                thread::sleep(Duration::from_millis(100));
            }
        });
        */

        Ok(())
    })
    .on_page_load(|window, payload| {
        // 可选：只对特定 label 的窗口执行
        if window.label() == "main" && payload.event() == PageLoadEvent::Finished {
            let _ = window.eval(js_eval::inject());
        }
    })
    .invoke_handler(tauri::generate_handler![greet,download::download_file_async,download::download_file_async_without_redirect,api::cloud_search,api::top_playlist,api::playlist_hot])
    .run(tauri::generate_context!())
    .expect("启动失败");
}
