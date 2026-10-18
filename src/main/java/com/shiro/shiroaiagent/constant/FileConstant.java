package com.shiro.shiroaiagent.constant;

/**
 * 文件常量
 */
public interface FileConstant {

    /**
     * 文件保存目录
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";

    /**
     * 知识库文档存储目录
     *
     * 管理员上传的 Markdown 与内置语料都落在这里，路径记录在
     * {@code kb_document.file_path}。向量入库与删除都从这个路径读文件。
     */
    String KB_DIR = FILE_SAVE_DIR + "/kb";
}
