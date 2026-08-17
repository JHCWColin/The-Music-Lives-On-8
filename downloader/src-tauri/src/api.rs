use ncm_api_rs::{create_client, Query};

#[tauri::command]
pub async fn cloud_search(keywords: String, t: String, limit: String) -> Result<String, String> {
    let client = create_client(None);
    let query = Query::new()
        .param("keywords", &keywords)
        .param("type", &t)
        .param("limit", &limit);

    match client.cloudsearch(&query).await {
        Ok(resp) => Ok(resp.body.to_string()),   // 紧凑 JSON 字符串
        Err(e) => Err(format!("搜索失败: {}", e)),
    }
}


#[tauri::command]
pub async fn top_playlist(limit: String) -> Result<String, String> {
    let client = create_client(None);
    let query = Query::new().param("limit", &limit);

    match client.top_playlist(&query).await {
        Ok(resp) => Ok(resp.body.to_string()),
        Err(e) => Err(format!("获取歌单失败: {}", e)),
    }
}

#[tauri::command]
pub async fn playlist_hot() -> Result<String, String> {
    let client = create_client(None);
    let query = Query::new();

    match client.playlist_hot(&query).await {
        Ok(resp) => Ok(resp.body.to_string()),
        Err(e) => Err(format!("获取热门歌单标签失败: {}", e)),
    }
}