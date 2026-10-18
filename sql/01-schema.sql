-- 说明：
--   1. 向量库表（vector_store）由 Spring AI 自动建表，本脚本只建【业务表】；
--   2. 业务表分四组：组织与账号 / 学生与教务 / 知识库与审核 / 运营与安全；
--   3. 每张表带中文注释，可直接作为 ER 图与数据库设计章节的依据；
--   4. 个人数据（成绩、绩点）只落在业务表，**绝不进入向量库**。
--
--    本脚本是【开发期重建脚本】，开头会 DROP 下面这些表（会清空数据）。
--    库里有真实数据后请改用增量迁移脚本，不要重跑本文件。
--
-- 执行：psql -U <用户名> -d <数据库名> -f sql/01-schema.sql
-- ============================================================

-- ------------------------------------------------------------
-- 0. 清理（开发期重跑用；顺序：先子表后父表）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS kb_gap CASCADE;
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS blacklist CASCADE;
DROP TABLE IF EXISTS sensitive_word CASCADE;
DROP TABLE IF EXISTS qa_log CASCADE;
DROP TABLE IF EXISTS chat_session CASCADE;
DROP TABLE IF EXISTS campus_place CASCADE;
DROP TABLE IF EXISTS kb_document_review CASCADE;
DROP TABLE IF EXISTS kb_document CASCADE;
DROP TABLE IF EXISTS training_plan CASCADE;
DROP TABLE IF EXISTS gpa_rule CASCADE;
DROP TABLE IF EXISTS score CASCADE;
DROP TABLE IF EXISTS course CASCADE;
DROP TABLE IF EXISTS student CASCADE;
DROP TABLE IF EXISTS major CASCADE;
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_role CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;
DROP TABLE IF EXISTS sys_dept CASCADE;

-- ============================================================
-- 一、组织与账号（权限管理的基础：部门 + 用户 + 角色）
-- ============================================================

-- 部门 / 院系 / 校内机构（树形）
CREATE TABLE sys_dept (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT       DEFAULT 0,                   -- 父部门 id，0 表示顶级
    name        VARCHAR(64)  NOT NULL,                    -- 部门名称，如"计算机学院""图书馆"
    code        VARCHAR(32)  NOT NULL UNIQUE,             -- 部门编码
    dept_type   VARCHAR(16),                              -- 类型：学院/教务处/学工部/图书馆/就业中心/后勤/网络中心
    sort_no     INT          DEFAULT 0,                   -- 排序
    status      SMALLINT     DEFAULT 1,                   -- 1 启用 0 停用
    created_at  TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE  sys_dept IS '部门/院系：知识库可见范围与权限划分的基准单位';
COMMENT ON COLUMN sys_dept.parent_id IS '父部门 id，0 表示顶级部门';

-- 角色
CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,             -- 角色标识：student/teacher/dept_admin/super_admin
    name        VARCHAR(64)  NOT NULL,                    -- 角色名称
    remark      VARCHAR(200),
    status      SMALLINT     DEFAULT 1,
    created_at  TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE sys_role IS '角色：学生、教师、部门管理员、超级管理员';

-- 账号（学生、教师、管理员共用一张账号表）
CREATE TABLE sys_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,           -- 登录名（学生用学号）
    password      VARCHAR(128) NOT NULL,                  -- 密码哈希（BCrypt），禁止存明文
    real_name     VARCHAR(64),                            -- 姓名
    user_type     VARCHAR(16)  NOT NULL,                  -- 账号类型：student/teacher/admin
    dept_id       BIGINT       REFERENCES sys_dept(id),   -- 所属部门
    phone         VARCHAR(32),
    email         VARCHAR(64),
    status        SMALLINT     DEFAULT 1,                 -- 1 正常 0 禁用
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE  sys_user IS '账号表：登录鉴权的主体，学生账号与 student 表一对一';
COMMENT ON COLUMN sys_user.user_type IS '账号类型：student 学生 / teacher 教师 / admin 管理人员';

-- 用户-角色关联
CREATE TABLE sys_user_role (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户与角色的多对多关联';

-- ============================================================
-- 二、学生与教务（"我的成绩/绩点"的真实数据来源，走 Tool 查询）
-- ============================================================

-- 专业
CREATE TABLE major (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(32) NOT NULL UNIQUE,         -- 专业代码
    name             VARCHAR(64) NOT NULL,                -- 专业名称
    dept_id          BIGINT      REFERENCES sys_dept(id), -- 所属学院
    duration_years   INT         DEFAULT 4,               -- 学制
    total_credits    NUMERIC(5,1),                        -- 毕业要求总学分
    required_credits NUMERIC(5,1),                        -- 其中必修学分
    elective_credits NUMERIC(5,1),                        -- 其中选修学分
    remark           VARCHAR(200),
    created_at       TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE major IS '专业：含毕业学分要求，供"毕业要求"类问题查询';

-- 学生
CREATE TABLE student (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL UNIQUE REFERENCES sys_user(id), -- 关联登录账号
    student_no     VARCHAR(32) NOT NULL UNIQUE,           -- 学号
    name           VARCHAR(64) NOT NULL,                  -- 姓名
    gender         VARCHAR(4),                            -- 男/女
    dept_id        BIGINT      REFERENCES sys_dept(id),   -- 所属学院
    major_id       BIGINT      REFERENCES major(id),      -- 专业
    class_name     VARCHAR(64),                           -- 班级，如"计科2201"
    enroll_year    INT,                                   -- 入学年份
    grade          VARCHAR(16),                           -- 年级，如"2022级"
    gpa            NUMERIC(3,2),                          -- 平均绩点（可由 score 汇总刷新）
    earned_credits NUMERIC(5,1),                          -- 已获得学分
    status         SMALLINT    DEFAULT 1,                 -- 1 在读 2 休学 3 毕业 0 退学
    created_at     TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE  student IS '学生基本信息：个人数据类问题的数据源，禁止入库向量库';
COMMENT ON COLUMN student.gpa IS '平均绩点，建议由 score 表汇总计算后回写（或实时计算）';

-- 课程
CREATE TABLE course (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32) NOT NULL UNIQUE,             -- 课程代码
    name         VARCHAR(128) NOT NULL,                   -- 课程名称
    credit       NUMERIC(3,1) NOT NULL,                   -- 学分
    hours        INT,                                     -- 学时
    course_type  VARCHAR(16),                             -- 课程性质：必修/选修/实践
    assess_type  VARCHAR(16),                             -- 考核方式：考试/考查
    dept_id      BIGINT      REFERENCES sys_dept(id),     -- 开课单位
    status       SMALLINT    DEFAULT 1,
    created_at   TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE course IS '课程信息';

-- 成绩（个人数据核心表）
CREATE TABLE score (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT      NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    course_id  BIGINT      NOT NULL REFERENCES course(id),
    term       VARCHAR(16) NOT NULL,                      -- 学期，如 2023-2024-1
    score      NUMERIC(5,2),                              -- 成绩分数
    gpa_point  NUMERIC(3,2),                              -- 该课绩点（按 gpa_rule 换算）
    exam_type  VARCHAR(16) DEFAULT '正常考试',             -- 正常考试/补考/重修/缓考
    passed     BOOLEAN     DEFAULT FALSE,                 -- 是否通过（补考通过也记 true）
    remark     VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (student_id, course_id, term)
);
COMMENT ON TABLE  score IS '学生成绩：唯一权威数据源，问答时经鉴权后由工具实时查询';
COMMENT ON COLUMN score.term IS '学期，格式 yyyy-yyyy-学期序号，如 2023-2024-1';

-- 绩点换算规则（"绩点怎么算"既要能查规则，也要能算结果）
CREATE TABLE gpa_rule (
    id          BIGSERIAL PRIMARY KEY,
    score_min   NUMERIC(5,2) NOT NULL,                    -- 分数下限
    score_max   NUMERIC(5,2) NOT NULL,                    -- 分数上限
    gpa_point   NUMERIC(3,2) NOT NULL,                    -- 对应绩点
    grade_label VARCHAR(8),                               -- 等级：优秀/良好/中等/及格/不及格
    remark      VARCHAR(200)
);
COMMENT ON TABLE gpa_rule IS '绩点换算规则：既可进知识库讲规则，也用于成绩查询时计算绩点';

-- 培养方案（专业-课程-建议学期）
CREATE TABLE training_plan (
    id             BIGSERIAL PRIMARY KEY,
    major_id       BIGINT      NOT NULL REFERENCES major(id) ON DELETE CASCADE,
    course_id      BIGINT      NOT NULL REFERENCES course(id),
    recommend_term INT,                                   -- 建议修读学期 1-8
    course_attr    VARCHAR(16),                           -- 必修/选修
    min_credit     NUMERIC(3,1),                          -- 该课最低学分要求
    remark         VARCHAR(200),
    UNIQUE (major_id, course_id)
);
COMMENT ON TABLE training_plan IS '培养方案：支撑"毕业要求/还差多少学分"类问题';

-- ============================================================
-- 三、知识库与审核（文档上传、解析、审核、部门可见范围）
-- ============================================================

-- 知识库文档（含权限元信息：部门 + 可见范围）
CREATE TABLE kb_document (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,                 -- 文档标题
    file_name      VARCHAR(200),                          -- 原始文件名
    file_path      VARCHAR(500),                          -- 存储路径
    file_type      VARCHAR(16),                           -- md/pdf/docx/txt
    file_size      BIGINT,                                -- 字节数
    scene          VARCHAR(32),                           -- 场景分类：教务问答/学生事务/校园生活/图书馆服务/就业服务/校园导航
    dept_id        BIGINT REFERENCES sys_dept(id),        -- 归属部门（权限下推的依据）
    visibility     VARCHAR(16) DEFAULT '公开',             -- 可见范围：公开/本部门/指定角色
    status         VARCHAR(16) DEFAULT '待审核',           -- 待审核/已通过/已驳回/已下架
    uploader_id    BIGINT REFERENCES sys_user(id),        -- 上传人
    uploader_type  VARCHAR(16),                           -- 上传来源：管理员/用户
    reviewer_id    BIGINT REFERENCES sys_user(id),        -- 审核人
    reviewed_at    TIMESTAMPTZ,
    review_remark  VARCHAR(500),                          -- 审核意见
    chunk_count    INT         DEFAULT 0,                 -- 切分后的片段数
    vector_status  VARCHAR(16) DEFAULT '未入库',           -- 未入库/已入库/入库失败
    effective_date DATE,                                  -- 生效日期
    version        INT         DEFAULT 1,                 -- 版本号（重新入库时递增）
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE  kb_document IS '知识库文档：向量库的语料来源，权限元信息随文档一同写入向量库 metadata';
COMMENT ON COLUMN kb_document.visibility IS '可见范围，检索时作为 metadata filter 实现权限下推';

-- 审核记录（工作流留痕，答辩时可直接展示）
CREATE TABLE kb_document_review (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    reviewer_id BIGINT      REFERENCES sys_user(id),
    action      VARCHAR(16) NOT NULL,                     -- 提交/通过/驳回/下架
    remark      VARCHAR(500),
    created_at  TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE kb_document_review IS '文档审核轨迹：每次审核动作一条记录';

-- 校园办事地点（校园导航，走结构化查询而不是 RAG）
CREATE TABLE campus_place (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,                   -- 地点名称，如"教务处学籍科"
    category     VARCHAR(32),                             -- 分类：教务处/学工部/图书馆/食堂/宿舍/网络中心/就业中心
    dept_id      BIGINT REFERENCES sys_dept(id),
    building     VARCHAR(64),                             -- 楼栋
    room         VARCHAR(64),                             -- 房间号
    address      VARCHAR(200),                            -- 详细地址
    office_hours VARCHAR(200),                            -- 办公时间
    phone        VARCHAR(64),                             -- 联系电话
    keywords     VARCHAR(200),                            -- 检索关键词，如"补办学生证 缓考 成绩单"
    remark       VARCHAR(200),
    status       SMALLINT    DEFAULT 1,
    created_at   TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE  campus_place IS '校园办事地点：支撑"去哪办、几点办公、电话多少"类问题';
COMMENT ON COLUMN campus_place.keywords IS '供关键词/模糊匹配使用，弥补结构化数据难以语义检索的问题';

-- ============================================================
-- 四、运营与安全（日志、统计、敏感词、审计、缺口闭环）
-- ============================================================

-- 会话（支撑前端历史会话列表）
CREATE TABLE chat_session (
    id              BIGSERIAL PRIMARY KEY,
    chat_id         VARCHAR(64) NOT NULL UNIQUE,          -- 与后端 chatId 一致（记忆文件按此命名）
    user_id         BIGINT REFERENCES sys_user(id),
    title           VARCHAR(200),                         -- 会话标题（可取首问）
    message_count   INT         DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE chat_session IS '会话列表：与文件记忆（Kryo）配合，供前端展示历史会话';

-- 问答日志（统计分析与缺口闭环的唯一数据源）
CREATE TABLE qa_log (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES sys_user(id),
    chat_id        VARCHAR(64),
    question       TEXT        NOT NULL,                  -- 用户提问原文
    scene          VARCHAR(32),                           -- 意图分类结果（六类场景）
    channel        VARCHAR(16),                           -- 通道：RAG / Tool / RAG+Tool
    hit            BOOLEAN     DEFAULT FALSE,             -- 检索是否命中知识库
    confidence     NUMERIC(4,3),                          -- 检索相似度（最高分）
    tool_name      VARCHAR(64),                           -- 调用的工具名，如 queryMyScore
    cited_kb_ids   VARCHAR(200),                          -- 引用的知识库文档 id 列表
    cited_db       BOOLEAN     DEFAULT FALSE,             -- 答案是否引用了业务库数据
    answer_excerpt VARCHAR(1000),                         -- 回答摘要（可选，便于复盘）
    latency_ms     INT,                                   -- 端到端耗时
    token_in       INT,
    token_out      INT,
    feedback       SMALLINT    DEFAULT 0,                 -- 1 点赞 / -1 点踩 / 0 未评价
    created_at     TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE qa_log IS '问答日志：问答次数、活跃用户、热门问题、场景占比、缺口闭环的数据来源';

-- 知识库缺口清单（把统计从"报表"升级为"闭环"）
CREATE TABLE kb_gap (
    id              BIGSERIAL PRIMARY KEY,
    scene           VARCHAR(32),                          -- 缺口所属场景
    question_sample TEXT        NOT NULL,                 -- 代表性问题（未命中/低置信/被点踩）
    hit_count       INT         DEFAULT 1,                -- 累计被问次数
    last_asked_at   TIMESTAMPTZ,
    status          VARCHAR(16) DEFAULT '待处理',          -- 待处理/已补充/已忽略
    handler_id      BIGINT REFERENCES sys_user(id),
    handled_at      TIMESTAMPTZ,
    remark          VARCHAR(500),
    created_at      TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE kb_gap IS '知识库缺口清单：未命中问题聚合后提示管理员补语料，形成优化闭环';

-- 敏感词与黑名单词
CREATE TABLE sensitive_word (
    id         BIGSERIAL PRIMARY KEY,
    word       VARCHAR(100) NOT NULL UNIQUE,              -- 敏感词/黑名单词
    category   VARCHAR(32),                               -- 分类：政治敏感/辱骂/广告/违禁/自定义
    action     VARCHAR(16) DEFAULT '拦截',                 -- 拦截/提醒/仅记录
    scope      VARCHAR(16) DEFAULT '双向',                 -- 输入/输出/双向
    enabled    BOOLEAN     DEFAULT TRUE,
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE sensitive_word IS '敏感词库：输入侧拦截 + 输出侧复核，按 action 决定处置方式';

-- 黑名单（用户 / IP / 问答内容）
CREATE TABLE blacklist (
    id         BIGSERIAL PRIMARY KEY,
    list_type  VARCHAR(16) NOT NULL,                      -- 用户/IP/内容
    value      VARCHAR(128) NOT NULL,                     -- 具体值（用户 id、IP、内容指纹）
    reason     VARCHAR(200),
    expire_at  TIMESTAMPTZ,                               -- 过期时间，空表示永久
    enabled    BOOLEAN     DEFAULT TRUE,
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (list_type, value)
);
COMMENT ON TABLE blacklist IS '黑名单：账号/IP/内容三个维度，命中即拒绝服务';

-- 操作审计（谁上传/审核/改了权限）
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES sys_user(id),
    username    VARCHAR(64),
    action      VARCHAR(64) NOT NULL,                     -- 如 DOC_UPLOAD / DOC_APPROVE / ROLE_UPDATE
    target_type VARCHAR(32),                              -- 操作对象类型
    target_id   VARCHAR(64),                              -- 操作对象 id
    detail      JSONB,                                    -- 变更详情
    ip          VARCHAR(64),
    created_at  TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE audit_log IS '操作审计日志：管理端敏感操作留痕';

-- ============================================================
-- 五、常用索引（按实际查询场景，答辩可讲"索引设计"）
-- ============================================================
CREATE INDEX idx_score_student      ON score (student_id);
CREATE INDEX idx_score_course       ON score (course_id);
CREATE INDEX idx_score_term         ON score (term);
CREATE INDEX idx_student_major      ON student (major_id);
CREATE INDEX idx_student_dept       ON student (dept_id);
CREATE INDEX idx_kb_doc_status      ON kb_document (status, dept_id);
CREATE INDEX idx_kb_doc_scene       ON kb_document (scene);
CREATE INDEX idx_qa_log_created     ON qa_log (created_at DESC);
CREATE INDEX idx_qa_log_scene       ON qa_log (scene);
CREATE INDEX idx_qa_log_user        ON qa_log (user_id);
CREATE INDEX idx_qa_log_hit         ON qa_log (hit);
CREATE INDEX idx_campus_place_cat   ON campus_place (category);
CREATE INDEX idx_audit_log_created  ON audit_log (created_at DESC);

-- ============================================================
-- 六、统计分析用视图（可选，直接把"场景占比/热门问题"做成视图）
-- ============================================================

-- 六类场景问答占比
CREATE OR REPLACE VIEW v_scene_stat AS
SELECT COALESCE(scene, '未分类') AS scene,
       COUNT(*)                  AS ask_count,
       SUM(CASE WHEN hit THEN 1 ELSE 0 END) AS hit_count,
       ROUND(AVG(latency_ms))    AS avg_latency_ms
FROM qa_log
GROUP BY COALESCE(scene, '未分类');

COMMENT ON VIEW v_scene_stat IS '按场景统计问答次数与命中情况，供统计看板使用';

-- 热门问题 Top N（按问题文本归并，实际可再加语义聚类）
CREATE OR REPLACE VIEW v_hot_question AS
SELECT question,
       COUNT(*)        AS ask_count,
       MAX(created_at) AS last_asked_at
FROM qa_log
GROUP BY question
ORDER BY ask_count DESC;

COMMENT ON VIEW v_hot_question IS '热门问题统计视图';

