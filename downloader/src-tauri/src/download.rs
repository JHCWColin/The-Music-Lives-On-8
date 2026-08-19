//src-tauri/src/download.rs
use reqwest::blocking::Client;
#[cfg(not(target_os = "android"))]
use std::fs::File;
#[cfg(not(target_os = "android"))]
use std::io::Write;

#[cfg(target_os = "android")]
use tauri_plugin_android_fs::{AndroidFsExt, PublicGeneralPurposeDir};

#[tauri::command]
pub async fn download_file_async_without_redirect(
    app_handle: tauri::AppHandle,
    url: String,
    name: String,
) -> String {
    let result = tauri::async_runtime::spawn_blocking(move || -> Result<String, String> {
        let client = Client::builder()
            .redirect(reqwest::redirect::Policy::none())
            .build()
            .map_err(|e| format!("构建HTTP客户端失败: {}", e))?;

        let response = client
            .head(&url)
            .send()
            .map_err(|e| format!("HEAD请求失败: {}", e))?;

        let status = response.status();
        if !status.is_redirection() {
            return Err(format!("未返回重定向，状态码: {}", status));
        }

        let location = response
            .headers()
            .get(reqwest::header::LOCATION)
            .ok_or_else(|| "响应中缺少Location头".to_string())?
            .to_str()
            .map_err(|e| format!("Location头解析失败: {}", e))?;

        Ok(location.to_string())
    })
    .await;

    match result {
        Ok(Ok(final_url)) => download_file_async(app_handle, final_url, name).await,
        Ok(Err(err_msg)) => err_msg,
        Err(join_err) => format!("内部任务执行失败: {}", join_err),
    }
}

#[tauri::command]
pub async fn download_file_async(app_handle: tauri::AppHandle, url: String, name: String) -> String {
    let result = tauri::async_runtime::spawn_blocking(move || {
        download_file(app_handle, &url, &name)
    }).await;
    match result {
        Ok(return_value) => return_value,
        Err(join_error) => format!("内部错误: {}", join_error),
    }
}

/// 下载文件到系统下载目录（Downloads）
/// - url: 文件下载链接
/// - name: 文件名（不含扩展名），若为空则使用 "music"
/// - app_handle: Tauri 应用句柄
pub fn download_file(app_handle: tauri::AppHandle,url: &str, name: &str) -> String {
    #[cfg(not(target_os = "android"))]
    let _ = app_handle;
    #[cfg(not(target_os = "android"))]
    let dir = match dirs::download_dir() {
        Some(dir) => dir,
        None => {
            eprintln!("⚠️ 无法获取下载目录");
            return "⚠️ 无法获取下载目录".to_string();
        }
    };

    // 从 URL 提取扩展名（最后一个点之后的部分）
    let extension = url
        .split('/')
        .last()
        .and_then(|part| part.split('.').last())
        .unwrap_or("mp3");
    // 确保扩展名不带查询参数
    let extension = extension.split('?').next().unwrap_or("mp3");

    // 组合文件名：使用提供的 name，若为空则用 "music"
    let base_name = if name.is_empty() { "music" } else { name };
    let filename = format!("{}.{}", base_name, extension);
    // 直接保存到下载目录
    #[cfg(not(target_os = "android"))]
    let download_folder = dir;

    // 如果目录不存在，则创建（包括父目录）
    #[cfg(not(target_os = "android"))]
    if !download_folder.exists() {
        if let Err(e) = std::fs::create_dir_all(&download_folder) {
            eprintln!("❌ 创建目录失败: {}", e);
            return format!("❌ 创建目录失败: {}", e);
        }
        println!("📁 创建目录: {:?}", download_folder);
    }
    #[cfg(not(target_os = "android"))]
    let file_path = download_folder.join(filename);

    #[cfg(not(target_os = "android"))]
    println!("📥 开始下载: {} -> {:?}", url, file_path);

    #[cfg(target_os = "android")]
    println!("📥 开始下载: {} -> {}", url, filename);

    let client = Client::new();
    match client.get(url).send() {
        Ok(response) => {
            if response.status().is_success() {
                let bytes = match response.bytes() {
                    Ok(b) => b,
                    Err(e) => {
                        eprintln!("❌ 读取响应数据失败: {}", e);
                        return format!("❌ 读取响应数据失败: {}", e);
                    }
                };
                if bytes.is_empty() {
                    return "歌曲无内容，可能是无音源".to_string();
                }
                #[cfg(not(target_os = "android"))]
                match File::create(&file_path) {
                    Ok(mut file) => {
                        if let Err(e) = file.write_all(&bytes) {
                            eprintln!("❌ 写入文件失败: {}", e);
                            return format!("❌ 写入文件失败: {}", e);
                        } else {
                            println!("✅ 下载成功: {}", file_path.display());
                        }
                    }
                    Err(e) => {
                        eprintln!("❌ 创建文件失败: {}", e);
                        return format!("❌ 创建文件失败: {}", e);
                    }
                }

                #[cfg(target_os = "android")]
                let (tx, rx) = tokio::sync::oneshot::channel();
                #[cfg(target_os = "android")]
                let app_handle_c = app_handle.clone();

                #[cfg(target_os = "android")]
                tauri::async_runtime::spawn(async move {
                    let result = save_bytes_to_music_dir(app_handle_c, &bytes, filename).await;
                    let _ = tx.send(result);
                });

                // Android 平台使用 tauri-plugin-android-fs 保存文件
                #[cfg(target_os = "android")]
                match rx.blocking_recv() {
                    Ok(Ok(())) => println!("保存成功"),
                    Ok(Err(e)) => {
                        eprintln!("保存失败: {}", e);
                        return format!("保存失败: {}", e);
                    }
                    Err(_) => {
                        eprintln!("保存超时");
                        return "保存超时".to_string();
                    }
                }
            } else {
                eprintln!("❌ HTTP 错误: {}", response.status());
                return format!("❌ HTTP 错误: {}", response.status());
            }
        }
        Err(e) => {
            eprintln!("❌ 下载请求失败: {}", e);
            return format!("❌ 下载请求失败: {}", e);
        }
    }
    "Done".to_string()
}

#[cfg(target_os = "android")]
pub async fn save_bytes_to_music_dir(
    app: tauri::AppHandle,
    bytes: &[u8],
    file_name: String,
) -> Result<(), String> {
    let api = app.android_fs_async();
    let public = api.public_storage();

    // 检查并请求权限（Android 9 及以下需要，10+ 立即返回 true）
    let perm_granted = public
        .request_permission()
        .await
        .map_err(|e| format!("权限请求失败: {}", e))?;
    if !perm_granted {
        return Err("用户拒绝了存储权限".to_string());
    }

    // 写入文件（自动创建 musicdownloaded 目录，自动处理同名冲突）
    let _uri = public
        .write_new(
            None,                  // 主存储
            PublicGeneralPurposeDir::Download, // 目标目录：Downloads
            file_name,
            None, // 自动推断 MIME 类型
            bytes,
        )
        .await
        .map_err(|e| format!("保存文件失败: {}", e))?;

    Ok(())
}
