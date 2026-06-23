# 项目知识点（Vue2 → Vue3 迁移笔记）

> 目标：把”为什么 Vue2 能跑、Vue3 不行”的经验沉淀下来，方便后续迁移模块时快速定位问题。
>
> 更新约定：每次遇到**新坑/新规则/关键改动**，都在本文件追加一段（带日期时间），并在 `CHANGELOG.md` 记录对应代码变更。

## 更新记录

### 2026-04-28 11:45:00

- **个人首页下方趋势图如果要给某个病种单独体现“目标值”，优先在 `HealthCharts.vue` 的具体页签分支里加 `series.markLine`，不要先改通用数据结构**：
  - 这次冠心病需求是“先在血压里试试目标值”，目标只影响：
    - `src/views/tnb/personhome/components/HealthCharts.vue`
    - `GXB`
    - `xueya`
  - 当前血压图本身已经有两条折线：
    - 高压 `data.xueya_g`
    - 低压 `data.xueya_d`
  - 这种场景最小改法不是去扩展后端接口，也不是先改通用 tab 配置，而是直接在对应 `series` 上追加：
    - `markLine`
  - 并通过 `props.currentDisease === 'GXB' && tab === 'xueya'` 把影响面锁死在当前病种页签内。
  - 这样做的好处是：
    - 不会影响 `TNB / GXY / NZZ / MZF / MXSB` 的血压图
    - 不需要改现有图表数据结构和接口口径
    - 后续如果要继续补 `LDL-C / BMI / HbA1c` 的目标线，可以直接复用同一模式逐项加。~喵

### 2026-04-28 11:20:00

- **慢性肾病个人首页“治疗方案”卡片顶部绿色按钮文案在 `MxsbTreatmentPlanV2.vue` 本地写死，不是从病种通用映射里下发**：
  - 这次排查 `#/hzinfo/personhome/...?...disease=MXSB` 发现，慢性肾病个人首页并没有复用通用 `TreatmentPlan.vue` 的按钮标题映射，而是直接使用：
    - `src/views/tnb/personhome/components/MxsbTreatmentPlanV2.vue`
  - 其中顶部绿色按钮文字和空状态文案都是组件内模板写死：
    - `Vue版治疗方案`
    - `暂无 Vue版治疗方案`
  - 所以如果只想把个人首页这块改成“药物治疗方案”，最小改法不是去动弹窗标题或病种映射，而是直接改这个组件里的两处展示文案。
  - 这样可以保证：
    - 个人首页卡片文案立即对齐业务语义
    - 弹窗组件 `MxsbVueTreatmentPlanPreviewDialog.vue`、列表标题“慢性肾病治疗方案列表”、保存链路都不受影响。~喵

### 2026-04-27 17:45:32

- **糖尿病个人首页“控制目标”趋势如果一直是 `-`，要同时排查 `KZMB` 历史口径和 `dynfield` 是否还是 JSON 字符串**：
  - 这次糖尿病首页趋势不显示，根因不是箭头模板坏了，而是 `src/views/tnb/personhome/components/ControlTargets.vue` 里的 `TNB` 分支同时踩了两个点：
    - 最新记录仍走旧 `findByJbxx`
    - 历史比较时虽然查了上一条详情，但 `historyData.dynfield` 可能还是 JSON 字符串，未先解析成对象
  - 这样会出现页面“最近一次”能显示，但趋势比较阶段 `prevDynfield[key]` 取不到，所有指标都落成 `-`。
  - 对糖尿病控制目标卡片，更稳的口径是：
    - 最新值优先走 `findKzmbByJbxx`
    - 历史列表优先用 `getList + formKind_eq: 'KZMB'`
    - 当前值和历史值在读字段前都先统一做 `dynfield` JSON 解析
  - 只有把这三件事一起补齐，糖尿病 `KFXT / CH2H / THXHDB / LDL_C / HDL_C / TG / TC / BMI` 的趋势比较才会稳定生效。~喵

- **糖尿病个人首页“控制目标”卡片虽然也走通用 `ControlTargets.vue`，但它和高血压不同，新增入口可以直接复用现有 `YsMbTnbZhkzmbZsDrawer.newFormData`**：
  - 这次排查发现 `TNB` 与 `GXY` 都复用：
    - `src/views/tnb/personhome/components/ControlTargets.vue`
  - 但两者抽屉形态不同：
    - 高血压已经拆出独立 `YsMbGxyKzmbDrawer`
    - 糖尿病当前首页仍直接使用 `YsMbTnbZhkzmbZsDrawer`
  - 所以给糖尿病补 `+` 号时，最小改法不是再引入新抽屉，而是：
    - 标题栏按钮只在 `props.currentDisease === 'TNB'` 时显示
    - `openAddFromLast()` 里直接调用 `tnbDrawerRef.newFormData({ jbxxidid }, { scrollTo: 'controlTargets' })`
  - 这样能保持糖尿病首页原有“打开表单后滚到控制目标区”的交互，不会额外扩大本次修改范围。~喵

- **高血压个人首页“控制目标”卡片走的是通用 `ControlTargets.vue`，补 `+` 号时要只限制在 `GXY` 分支，避免误影响糖尿病同组件逻辑**：
  - 这次排查发现高血压并没有单独的 `GxyControlTargets.vue`，而是复用了：
    - `src/views/tnb/personhome/components/ControlTargets.vue`
  - 这个组件同时服务 `TNB / GXY / GXB` 等多个病种分支，所以新增按钮不能直接全局铺开。
  - 对高血压最稳妥的补法是：
    - 标题栏 `+` 按钮只在 `props.currentDisease === 'GXY'` 时显示
    - 新增独立 `YsMbGxyKzmbDrawer` ref
    - `openAddFromLast()` 只给 `GXY` 调 `newFormData({ jbxxidid })`
  - 这样可以把影响面控制在高血压分支内，不会顺手改动糖尿病共用组件的原有交互。~喵

- **慢性肾病个人首页“控制目标”卡片补 `+` 号时，也应直接接 `YsMbMxsbKzmbDrawer`，不要把新增入口误绑到分级抽屉**：
  - 这次排查 `src/views/tnb/personhome/components/diseases/mxsb/MxsbControlTargets.vue` 发现，页面原本和慢阻肺一样，只有编辑按钮，没有标题栏 `+`。
  - 但慢性肾病已经具备独立控制目标抽屉：
    - `src/views/mxsb/MbFenji/YsMbMxsbKzmbDrawer.vue`
  - 所以在个人首页给“控制目标”卡片补新增入口时，最稳妥的做法是：
    - 保留原 `YsMbMxsbFenjiDrawer` 编辑逻辑不变
    - 另加 `YsMbMxsbKzmbDrawer` 的 ref
    - `openAddFromLast()` 直接调用 `newFormData({ jbxxidid })`
  - 这样只补“新增控制目标”入口，不会顺手改坏当前慢性肾病控制目标的数据展示和趋势图链路。~喵

- **慢阻肺个人首页“控制目标”卡片如果只有编辑按钮、没有 `+`，补入口时应直接接 `YsMbMzfKzmbDrawer`，不要误接回分级抽屉**：
  - 这次排查 `src/views/tnb/personhome/components/diseases/mzf/MzfControlTargets.vue` 发现，页面原本只有编辑按钮，且组件里已经保留了分级抽屉 `YsMbMzfFenjiDrawer`。
  - 但慢阻肺已经拆出独立的控制目标抽屉：
    - `src/views/mzf/MbFenji/YsMbMzfKzmbDrawer.vue`
  - 所以给“控制目标”卡片补 `+` 号时，最小且语义正确的做法是：
    - 新增 `YsMbMzfKzmbDrawer` 的 ref
    - `openAddFromLast()` 直接调用 `newFormData({ jbxxidid })`
  - 这样可以只补新增入口，不影响现有编辑按钮、趋势图和列表渲染链路。~喵

- **脑卒中个人首页“控制目标”卡片如果仍查 `findListByJbxx`，趋势列很容易一直显示 `-`，因为它读到的可能不是独立 `KZMB` 历史**：
  - 这次趋势不显示，根因不是箭头模板坏了，而是 `NzzControlTargets.vue` 的控制目标卡片仍在沿用：
    - `findListByJbxx`
  - 在脑卒中已拆出 `FENJI / KZMB` 两类记录后，这个旧列表口径未必能稳定返回多条可比较的控制目标历史。
  - 结果就是页面虽然能展示“最新一次”的值，但 `rowsWithValue[1]` 很可能取不到，趋势比较时只能落成 `-`。
  - 更稳的做法应与冠心病控制目标卡片保持一致：
    - 优先查 `findKzmbListByJbxx`
    - 若患者还没有独立 `KZMB` 历史，再临时回退 `findListByJbxx`
  - 一旦控制目标卡片的主数据源切到 `KZMB`，编辑按钮也要同步切到 `YsMbNzzKzmbDrawer`，否则会出现“卡片展示的是控制目标，编辑却打开分级表单”的语义错位。~喵

- **个人首页脑卒中“控制目标”卡片的 `+` 和“编辑”不要默认共用同一个 drawer，要先分清按钮操作的是“控制目标”还是“分级”**：
  - 这次排查 `src/views/tnb/personhome/components/diseases/nzz/NzzControlTargets.vue` 发现：
    - `+` 号调用 `openAddFromLast()`
    - 编辑按钮调用 `goToEdit()`
    - 但两者原先都连到了 `YsMbNzzFenjiDrawer`
  - 结果就是用户在“控制目标”卡片点 `+` 时，实际弹出的却是“脑卒中分级”表单，而不是控制目标表单。
  - 当病种已经拆出独立 `FENJI / KZMB` 表单后，首页聚合卡片的入口要按语义拆开：
    - 新建控制目标：`YsMbNzzKzmbDrawer.newFormData({ jbxxidid })`
    - 编辑最新分级：`YsMbNzzFenjiDrawer.show({ rid })`
  - 结论是：首页卡片标题栏里长得一样的按钮，不代表可以复用同一个弹窗；要先确认它操作的是哪类业务对象。~喵

- **脑卒中个人首页“精细化管理”读数时，不能复用 `@/api/nzz/MbNzz1zhkzmbZs_api.findByJbxx`，因为这个接口语义已经偏向“最新综合控制目标”**：
  - 这次脑卒中个人首页出现“精细化管理没内容”，根因不是字典坏了，而是 `RefinedManagement.vue` 取错了数据源。
  - 页面原来调用的是：
    - `@/api/nzz/MbNzz1zhkzmbZs_api.findByJbxx`
  - 但在脑卒中已经拆出 `FENJI / KZMB` 两类记录后，这个接口更容易返回最新的 `KZMB`（综合控制目标）记录。
  - 一旦首页拿到的是控制目标记录，就会出现：
    - `jbfl / guanliFenlei / nzzwxysfcResult` 这些分级字段为空
    - 脑卒中“精细化管理”卡片看起来像没数据
  - 个人首页脑卒中管理区如果要展示分级/精细化信息，应优先使用：
    - `@/api/naozuzhong/MbNzz1zhkzmbZs_api.findFenjiByJbxx`
  - 控制目标卡片则继续走控制目标自己的接口或列表，不要和分级卡片混用。~喵

- **脑卒中个人首页读取 `dynfield` 时，最好统一先做 JSON 解析，不要假定接口一定回对象**：
  - 这次一起顺手补了 `parseJsonObject(nzzZhkzmb.value?.dynfield)`。
  - 原因是脑卒中这条链路里 `dynfield` 可能是：
    - 已展开对象
    - JSON 字符串
  - 如果个人首页直接 `value?.dynfield || {}`，当接口返回字符串时，`jbfl / guanliFenlei` 会继续读空。
  - 对脑卒中这类仍在新旧链路混跑的模块，首页聚合组件做一次统一解析最稳。~喵

- **慢阻肺个人首页“精细化管理”也不能直接复用 `findByJbxx`，拆分后应明确切到 `findFenjiByJbxx`**：
  - 这次慢阻肺个人首页无数据，根因和脑卒中一致，都是 `RefinedManagement.vue` 取错了来源。
  - 页面原来调用：
    - `@/api/mzf/MbMzf1zhkzmbZs_api.findByJbxx`
  - 但慢阻肺已经具备：
    - `findFenjiByJbxx`
    - `findKzmbByJbxx`
    两条拆分后的链路。
  - 个人首页的“精细化管理”卡片如果继续读 `findByJbxx`，就可能命中最新的 `KZMB` 记录，导致 `MZF_ICD / guanliFenlei / fanghuFenji` 这些分级字段为空。
  - 所以慢阻肺个人首页应与分级表单保持一致，精细化管理展示统一走：
    - `findFenjiByJbxx`
  - 控制目标卡片则继续走控制目标自己的接口或列表，不要混用。~喵

- **慢阻肺个人首页读取 `dynfield` 也要做 JSON 解析，和脑卒中/慢性肾病保持一致**：
  - 这次同步把：
    - `const mzfDynfield = computed(() => mzfZhkzmb.value?.dynfield || {})`
    改成了：
    - `const mzfDynfield = computed(() => parseJsonObject(mzfZhkzmb.value?.dynfield))`
  - 原因同样是 `dynfield` 在接口返回里可能是 JSON 字符串，不一定已经是对象。
  - 如果个人首页直接按对象读取，就会出现：
    - 实际有 `MZF_ICD / guanliFenlei`
    - 页面却显示“无”
  - 对这类首页聚合卡片，统一先 parse 再读字段，比每个病种各自假定返回结构更稳。~喵

- **高血压个人首页“精细化管理”如果沿用 `findByJbxx`，要注意它实际走的是 `/findLastByJbxx` 老链路，不一定等于分级数据**：
  - 这次高血压个人首页无数据，根因不是模板完全坏了，而是来源接口还停留在：
    - `@/api/gaoxueya/MbGxy1zhkzmbZs_api.findByJbxx`
  - 这个方法在当前 API 文件里实际映射的是：
    - `/api/vab/mbGxy1zhkzmbZs/findLastByJbxx`
  - 也就是说它取的是“最近一次综合记录”，并不一定明确等于分级记录。
  - 但高血压当前前端已经拆出两条更明确的链路：
    - `findFenjiByJbxx`
    - `findKzmbByJbxx`
  - 所以个人首页里“精细化管理”这块，如果目标是展示分级/管理分类/防护分级，就应优先使用：
    - `findFenjiByJbxx`
  - 控制目标卡片则继续走控制目标链路，不要再和 `findLastByJbxx` 混用。~喵

- **高血压这条线判断“接口坏了”之前，先分清是“接口没值”还是“个人首页读错语义接口”**：
  - 高血压接口注释样例里其实已经能看到它会返回：
    - `gaoxueyaFenleiCh`
    - `gaoxueyaZhongleiCh`
    - `xxgFxFencengCh`
    - `xxgFxFenqiCh`
    - `fanghuFenjiCh`
  - 所以如果个人首页这一块显示为空，先不要直接怀疑模板字段名全错。
  - 更高概率的问题是：
    - 页面拿的不是“分级记录”
    - 而是“最近一次综合记录/控制目标记录”
  - 对高血压这种保留老接口别名的病种，优先确认接口语义，再决定要不要补字段兼容。~喵

- **糖尿病个人首页“精细化管理”除了可能取错接口，还要特别注意 `props.jbxx?.zhkhbm` 会不会把新请求数据抢掉**：
  - 这次糖尿病个人首页无数据，不只是来源接口停留在老的：
    - `findByJbxx11 -> /findByJbxx`
  - 还因为页面原先写的是：
    - `const tnbZhkzmb = computed(() => props.jbxx?.zhkhbm || tnbZhkzmbData.value || {})`
  - 这会导致只要 `props.jbxx.zhkhbm` 有一个旧对象、空对象或非分级结构对象，就会直接把后面真正请求回来的 `tnbZhkzmbData` 挡掉。
  - 表现上就会变成：
    - 网络里已经取到了新的分级数据
    - 页面仍然显示“无”
  - 所以糖尿病个人首页这块要同时做两件事：
    - 来源切到 `findFenjiByJbxx`
    - `computed` 取值顺序改成优先 `tnbZhkzmbData`
  - 否则即使接口改对，旧 `zhkhbm` 也可能继续覆盖正确结果。~喵

- **糖尿病这条线和高血压/脑卒中不同，真正的风险不一定是“模板字段名错”，而是“旧首页聚合对象优先级太高”**：
  - 当前糖尿病个人首页展示逻辑读的是：
    - `jbflCh / jbfl`
    - `guanliFenleiCh / guanliFenlei`
    - `fanghuFenjiCh / fanghuFenji`
    - `xxgwxysfcResultCh / xxgwxysfcResult`
  - 这些字段本身并不离谱，问题在于它们先从哪里取。
  - 如果数据先被 `props.jbxx.zhkhbm` 占位，后续 `tnbZhkzmbData` 再正确也没机会生效。
  - 所以排查糖尿病个人首页空白时，要把“数据优先级顺序”也当成根因排查项，不要只盯接口和字段名。~喵

- **慢性肾病个人首页“控制目标”在拆出独立 `KZMB` 后，不能继续查 `findListByJbxx` 分级列表**：
  - 这次慢性肾病个人首页控制目标为空，根因不是表格本身不渲染，而是 [MxsbControlTargets.vue] 取数还停在：
    - `findListByJbxx`
  - 但慢性肾病当前已经明确拆成两条链路：
    - 分级：`findListByJbxx / findFenjiByJbxx`
    - 控制目标：`findKzmbListByJbxx / findKzmbByJbxx`
  - 如果个人首页控制目标继续查分级列表，就会出现：
    - `KZMB` 明明已经单独保存
    - 首页控制目标仍然“暂无数据”
  - 所以慢性肾病个人首页控制目标区应优先改为：
    - `findKzmbListByJbxx`
  - 详情补拉和行归一化可以先沿用现有 `get + normalizeMxsbFenjiControlRow`，先把正确数据源接上，再决定是否需要进一步区分字段结构。~喵

- **慢性肾病个人首页下方图表如果展示 `eGFR / 尿白蛋白/肌酐 / 血压 / HbA1c`，数据源也必须同步切到 `KZMB` 列表**：
  - 这次慢性肾病首页图表空白，不是 tabs 配置没生成，而是 [HealthCharts.vue] 的 `MXSB` 分支还停在：
    - `findListByJbxx`
  - 但它的 `buildMxsbChartData()` 实际读的是：
    - `EGFR_TARGET`
    - `UACR_TARGET`
    - `SBP_TARGET`
    - `DBP_TARGET`
    - `HBA1C_TARGET`
  - 这些字段本身就是慢性肾病 `KZMB` 控制目标字段，不属于分级列表该负责的数据。
  - 所以会出现典型现象：
    - 首页控制目标和图表都显示空白
    - 但 tab 标题仍然正常出现
  - 对慢性肾病这条线，个人首页“控制目标表格”和“下方趋势图”应统一使用：
    - `findKzmbListByJbxx`
  - 只要图表构建函数本身已经按 `KZMB` 字段取值，通常只切数据源就能恢复出图。~喵

### 2026-04-24

- **Element Plus 的下拉颜色如果既要影响选项弹层，也要影响选中态，通常要同时处理组件本体和 `popper` 弹层**：
  - 这次“患者级别”需要在下拉列表里给 `绿标 / 黄标 / 红标` 上色，同时收起下拉后，输入框里的已选值也要保留颜色。
  - 仅在组件内写 `.patient-level-option` 样式，通常只能影响当前 SFC 范围内的 DOM；
  - 但 `el-select` 的下拉面板默认会 Teleport 到外层，因此弹层里的选项样式要配合：
    - `popper-class`
    - `:global(...)` 或等效的全局选择器
  - 而选中后的显示值仍在当前组件树里，适合通过：
    - 给 `el-select` 根节点加状态 class
    - 再用 `:deep(.el-select__selected-item / .el-input__inner)` 改颜色
  - 这类“同一个下拉要同时改弹层和选中态”的需求，最好把颜色映射、文本映射、风险 class 映射都集中成 helper，后续更稳。~喵

- **如果想把单选下拉做成“截图那种色块标签风格”，可以让选项和选中态共用同一套风险 class**：
  - 这次“患者级别”最终不是只做文字变色，而是升级成：
    - 下拉项：`色块 + 文案 + 浅色底`
    - 选中态：输入框内也显示同风格的小标签
  - 一个稳妥做法是：
    - `el-option` 插槽里自己渲染 `swatch + text`
    - `el-select` 根节点挂风险 class
    - `:deep(.el-select__selected-item)` 上补 `padding / 圆角 / 背景色`
    - 再用 `::before` 放选中态的小色块
  - 这样不用接管整套 Select 组件，也能做出接近设计稿的“标签式单选框”，同时保留 Element Plus 自带的键盘、清空、下拉行为。~喵

- **同一页面里多个病种分级下拉，最好不要按字典类型分别硬编码颜色，而是按“文案语义”做通用识别**：
  - 这次“编辑基本信息 -> 纳入慢病统计”里 6 个病种分级下拉分别来自：
    - `GXY_GLJB`
    - `TNB_GLJB`
    - `GXB_GLJB`
    - `MZF_GLJB`
    - `NZZ_GLJB`
    - `MXSB_GLJB`
  - 如果每个字典单独写颜色映射，后续很容易漏维护。
  - 更稳的做法是抽一个通用 helper，按文案包含关系统一识别：
    - `低危`
    - `中危`
    - `高危`
    - `很高危`
    - `极高危`
  - 这样只要字典展示文本里带这些词，就能自动套标签样式；不符合风险语义的项则保持普通显示，不会误染色。~喵

- **把下拉标签做成“更扁一点”的参考尺寸时，最好同时收 4 个参数，而不是只改 padding**：
  - 这次为了让风险标签更接近紧凑型样式，最终一起收了：
    - 下拉项 `gap`
    - 下拉项 `padding / min-width / border-radius`
    - 色块 `width / height / radius`
    - 选中态 `selected-item` 的 `padding` 与 `wrapper` 最小高度
  - 如果只缩文字 padding，不缩色块和选中态左侧占位，视觉会显得还是“高而空”。
  - 这类标签式 Select 要压高度，通常要一整组联动微调，效果才自然。~喵

- **如果目标是把“整行”压到参考图高度，不能只改 Select 标签本身，还要一起看表格和同排控件**：
  - 这次“纳入慢病统计”里真正撑高一行的，不只有分级下拉，还包括：
    - 表格 `th/td` 的上下 padding
    - 日期选择器 wrapper 高度
    - 同排小按钮高度
  - 所以更稳的做法是给这一块单独加局部类，例如 `disease-level-table`，再只覆盖这块区域的：
    - 单元格高度和 padding
    - `el-select__wrapper`
    - `el-date-editor` / `el-input__wrapper`
    - `el-button--small`
  - 这样才能把“视觉上一整行”压下来，而且不会误伤其他普通表单区。~喵

- **如果页面看起来“完全没变化”，要优先排查是不是被全局基础样式盖掉了**：
  - 这次“纳入慢病统计”行高第一版看起来几乎没变化，根因不是局部样式没写对，而是全局：
    - `src/styles/jbxx-editor.css`
    - `table.biao th, table.biao td { padding: 10px 12px; }`
  - 这类基础表格样式往往作用整个页面，局部规则如果选择器不够强，就会被它继续顶住。
  - 处理办法是：
    - 给局部表格加更具体的类，例如 `.biao.disease-level-table`
    - 直接覆盖 `th/td` 的 `padding / height / vertical-align`
    - 必要时对局部覆盖使用 `!important`
  - 先确认“真正撑高的是谁”，再改局部样式，效率会高很多。~喵

- **“筛查”按钮这种按病种显示/隐藏的入口，最好把规则集中在单一方法里改，不要散改 6 处模板**：
  - 这次“纳入慢病统计”里 6 个病种的“筛查”按钮都走：
    - `showDiseaseScreeningButton(code)`
  - 当需求从“已勾选才显示”改成“未勾选才显示”时，最稳妥的改法是只翻转这个方法里的布尔条件：
    - 原来：`includes(code)`
    - 现在：`!includes(code)`
  - 这样模板 `v-if`、按钮点击事件和弹窗链路都不用动，回归范围最小。~喵

- **这种“单独一行的标签式下拉”如果只想收成半宽，优先给它单独类，不要直接改整页 Select 宽度**：
  - 这次新建表单里“患者级别”改成了“整体级别”，同时希望只把这一行的下拉缩到半宽。
  - 更稳的做法是：
    - 模板上给它单独类，例如 `overall-level-select`
    - 行内宽度先设 `50%`
    - 样式里再补一个 `max-width`
  - 这样既能在大弹窗里看起来不那么空，又不会误伤其他复用同类风险标签样式的下拉框。~喵

- **分级表单里字段标题改名时，最好连必填校验提示一起改，不要只改表头**：
  - 这次冠心病分级表单把“基本分类”改成了“病种ICD”。
  - 如果只改页面表头，不改 `el-form-item` 里的必填 `message`，用户在校验报错时还会看到旧文案，体验会割裂。
  - 这类字段文案调整，至少要同步检查：
    - 表格/表单标题
    - placeholder
    - 校验 message
    - 相关 tooltip 或说明文字
  - 文案统一通常是小改动，但非常容易遗漏在校验提示层。~喵

- **像冠心病这种“分级页展示一个颜色级别，但个人首页读取的是当前管理信息字段”的场景，要分清“展示表”和“真实来源表”**：
  - 这次冠心病分级页希望展示并保存“红标/黄标/绿标”对应的疾病分级。
  - 但项目里真正给个人首页、页头标签等下游使用的，不是冠心病分级主表，而是：
    - `mbJbxx05dqglxx.gxbGljb`
  - 冠心病分级页自己的主表 `mbGxb1zhkzmbZs` 现有字段主要还是：
    - `dynfield.GXB_ICD`
    - `dynfield.guanliFenlei`
    - 控制目标等
  - 所以这类需求要拆成两步：
    - 打开分级页时，从 `mbJbxx05dqglxx` 回显 `gxbGljb`
    - 保存分级页成功后，再额外同步一次 `mbJbxx05dqglxx.doEdit`
  - 如果只把值存在分级主表里，页面当下也许能显示，但个人首页和其他读取 `dqglxx` 的地方还是不会更新。~喵

- **如果同一张主表要同时承载“分级表单”和“综合控制目标表单”，最稳的做法是给主表加类型字段，而不是让明细表裸奔**：
  - 这次冠心病最终采用的是：
    - 主表：`MB_GXB_1ZHKZMB_ZS`
    - 明细表：`MB_GXB_ZHKZMB_ITEM_ZS`
    - 新增主表字段：`FORM_KIND`
  - 建议值：
    - `FENJI`
    - `KZMB`
  - 这样查询时仍然保持：
    - `ITEM.BATCH_FID = MAIN.RID`
  - 只是主表再多一个类型过滤，不会出现“只有明细没有主表”的孤儿数据问题。~喵

- **把综合控制目标从分级表单拆出去后，要顺手清理原分级表单里的历史请求逻辑，不然页面看着拆了，网络还在白跑**：
  - 这次冠心病分级页把综合控制目标独立出去之后，`GxbFenjiForm.vue` 里原来为了“最近一次结果 / 图表按钮”保留的这些逻辑就已经没意义了：
    - `findKzzb`
    - `queryKzzbList`
    - `loadKzzbRidByCode`
    - `loadLastKzzbVals`
    - `showEchart`
    - 以及配套的 `KZMB_ITEMS / kzzbRidByCode / lastKzzbValByRid`
  - 如果不一起删，点击“新建分级方案”虽然界面上已经没有控制目标区，但网络面板仍然会继续请求控制目标历史。
  - 这类“UI 已移除但请求还在”的残留逻辑，拆表单后最好立刻清理。~喵

- **高血压和冠心病虽然都能走 `FORM_KIND` 拆分，但高血压不能机械照搬冠心病的明细保存模型**：
  - 冠心病明细表是：
    - `FIELD_CODE + VAL_TEXT`
  - 高血压明细表是：
    - `DICT_TYPE + DICT_KEY + IS_SELECTED + VAL_TEXT`
  - 所以高血压拆出“综合控制目标”独立表单时：
    - 拆分思路可以复用
    - 但前端 payload 仍应保留 `fffcfg['MB_GXY_ZHKZMB___xxx']`
    - 后端保存逻辑仍要按 `DICT_TYPE / DICT_KEY / IS_SELECTED` 去落表
  - 适合复用的是“分成 `FENJI / KZMB` 两类主记录”的架构，不是具体的明细序列化方式。~喵

- **糖尿病这条线和冠心病更接近，可以直接复用“主表分类型 + 明细表按 FIELD_CODE 保存”的拆分方式**：
  - 糖尿病主表：
    - `MB_TNB_1ZHKZMB_ZS`
  - 糖尿病明细表：
    - `MB_TNB_ZHKZMB_ITEM_ZS`
  - 明细表结构核心就是：
    - `BATCH_FID`
    - `FIELD_CODE`
    - `VAL_TEXT`
  - 后端 `FastMbTnb1zhkzmbZsServiceImpl` 本来就是遍历 `dynfield` 按 `FIELD_CODE -> VAL_TEXT` 落库，所以拆成：
    - `FENJI`
    - `KZMB`
    两类主记录时，整体改法可以直接对齐冠心病。~喵

- **糖尿病前端拆分时，原分级表单里除了删除控制目标区块本身，还要同步移除一整串 UI 辅助逻辑**：
  - 这次 `renyuanFenjijibieForm.vue` 里原本和控制目标强绑定的，不只是模板区块，还有：
    - `MB_TNB_ZHKZMB`
    - `lastSf`
    - `pendingScrollTarget`
    - `showEchart()`
    - `scrollToPendingSection()`
    - `ZBECHART`
  - 如果只删模板，不删这些辅助状态和方法，分级表单仍会保留无用状态，甚至继续响应旧入口参数。
  - 所以糖尿病拆分时，应该和冠心病一样把“控制目标 UI + 请求 + 状态 + 滚动定位 + 图表弹层”作为一整组移走。~喵

- **个人首页控制目标在切到新主表类型时，最好先做“新链路优先、旧链路兜底”的兼容过渡**：
  - 这次冠心病控制目标从 `FENJI` 拆到 `KZMB` 后，个人首页不能立刻只读 `KZMB`，因为历史数据还都在旧分级记录里。
  - 更稳的过渡方式是：
    - 首页列表先查 `findKzmbListByJbxx`
    - 如果为空，再临时回退 `findListByJbxx`
  - 图表趋势也要同样处理：
    - 如果患者已经有 `KZMB` 记录，就只读 `KZMB`
    - 如果还没有，再回退旧 `FENJI`
  - 这样既能让新数据从今天起走新链路，也不会让旧患者首页一下子全空。~喵

- **同一病种拆出“综合控制目标列表”后，列表层也要复用原分级列表的字段归一化，不然数据库有值页面还是会空**：
  - 这次慢性肾病综合控制目标列表查库有记录，但“服务日期 / 操作时间”显示为空。
  - 根因不是接口没返回，而是综合控制目标列表直接用了原始行；分级列表那边其实已经有：
    - `normalizeMxsbFenjiRow()`
  - 这个 helper 会把：
    - `SERVICE_DATE`
    - `LAST_SERVICE_DATE`
    - `CREATE_TIME`
    - `UPDATE_TIME`
    统一映射成：
    - `serviceDate`
    - `lastServiceDate`
    - `createTime`
    - `updateTime`
  - 如果综合控制目标列表模板也在读 `row.serviceDate / row.updateTime`，那就必须先做同样的归一化。~喵

- **慢性肾病这条链路和冠心病不同：controller/mapping 已经带了 `FORM_KIND`，但 service 如果手工组 SQL 参数漏传，最终库里还是会空**：
  - 这次 `MB_MXSB_1ZHKZMB_ZS.FORM_KIND` 保存为空，不是前端没走 `saveKzmb`，也不是 controller 没设 `KZMB`。
  - 真正的问题在 `FastMbMxsb1zhkzmbZsServiceImpl.doSaveIt()`：
    - 它没有像冠心病那样直接 `save(vo)`
    - 而是手工组 `params` 调 MyBatis 的 `insertMain/updateMain`
  - 虽然 mapper SQL 里已经有：
    - `FORM_KIND = #{FORM_KIND}`
  - 但如果 Java `params` 没有：
    - `params.put("FORM_KIND", vo.getFormKind())`
    最终写库时这个列仍然会是空。
  - 这种“controller 和 mapper 都对，但 service 中间层手工组参漏字段”的问题，在 MyBatis 手写保存链路里很常见。~喵

- **脑卒中复用“分级 / 综合控制目标”拆分方案时，核心兼容点在 `XY` 与 `XYG/XYD` 的双向转换**：
  - 脑卒中控制目标当前不是冠心病那套 7 项，而是：
    - `XYG`
    - `XYD`
    - `XT`
    - `XZ`
  - 其中血压历史数据可能仍然保存在：
    - `XY`
  - 所以脑卒中独立 `KZMB` 表单要做两件事：
    - 回显时：把 `XY` 反拆成 `XYG / XYD`
    - 保存时：把 `XYG / XYD` 再合成为 `XY`
  - 只做其中一边，都会导致：
    - 旧数据看不见
    - 或新数据保存后老链路读不到。~喵

- **慢阻肺和冠心病/脑卒中的拆分方案表面一致，但控制目标字段编码要按慢阻肺自己的小写 code 走 item 表**：
  - 这次慢阻肺最终也拆成：
    - `FENJI`
    - `KZMB`
  - 但慢阻肺综合控制目标的字段编码当前采用的是前端已有 code：
    - `fev1Pred`
    - `fvc`
    - `catScore`
    - `mmrcScore`
    - `jxzjCs`
  - 既然你要求新数据统一存 `MB_MZF_ZHKZMB_ITEM_ZS`，那后端 `queryKzzbList` 也要同步按这些 `FIELD_CODE` 去 item 表查，不能继续查主表固定列。
  - 这类从“主表列口径”切到“item 表口径”的病种，保存和查询必须一起改，不然会出现：
    - 保存成功
    - 但最近一次结果和图表全空。~喵

- **慢性肾病这条线和前面几种病最大的差别，是它本来就走 MyBatis 主表 + item 表，不是简单 JPA 查询**：
  - 这次慢性肾病拆分 `FENJI / KZMB` 时，真正要改的是：
    - `MbMxsb1zhkzmbZsVabApiCtrl.java`
    - `MbMxsb1zhkzmbZs_Mapper.xml`
  - 因为它的查询和保存主要都在 controller + mapper 里完成，而不是靠 service 层统一模板。
  - 所以像：
    - `findLastByJbxx`
    - `findListByJbxx`
    - `countSameDay`
    - `insertMain`
    - `updateMain`
  - 都要一起把 `FORM_KIND` 带进去，不能只改前端接口名。~喵

- **慢性肾病拆完后，原分级表单里和控制目标有关的“最近一次结果 / 图表按钮”也要一起移走**：
  - 这次 `MxsbFenjiForm.vue` 原来直接带：
    - `KZMB_ITEMS`
    - `lastTargetValues`
    - `queryKzzbList`
    - `showEchart`
  - 一旦独立出 `MxsbKzmbForm.vue`，这些逻辑如果还留在分级表单里，就会继续发控制目标请求，和冠心病/脑卒中拆分前一样变成“页面没了，请求还在”。
  - 所以慢性肾病也要遵守同一条经验：拆出独立 `KZMB` 表单后，原分级表单必须把控制目标请求链路一起清掉。~喵

- **拆成两类记录后，“同日去重”必须把类型字段一起算进去**：
  - 原冠心病 `doEdit` 只按：
    - `mbJbxxFid + suifangRq`
    去重。
  - 一旦引入：
    - `FENJI`
    - `KZMB`
    两类记录，就允许同一患者同一天各有一条。
  - 所以后端去重条件必须升级成：
    - `mbJbxxFid + suifangRq + formKind`
  - 不然新增综合控制目标时，会被已有分级记录误判成“同日重复”。~喵

- **Element Plus 下拉如果只想改显示文案，不要改 `value`，只改 `el-option` 的 `label` 即可**：
  - 这次“所有患者名单 -> 新建”里的“患者级别”需要把：
    - `低危 -> 绿标`
    - `中危 -> 黄标`
    - `高危 -> 红标`
  - 但保存链路不能变，后端仍然要收到原始级别值。
  - 在 `el-select` / `el-option` 这类场景里，最稳妥的做法是：
    - `:value` 继续使用原字典值
    - 只通过本地格式化函数改 `:label`
  - 这样选中后的输入框展示会跟着变成新文案，但提交给表单和接口的值保持原样，不需要联动改保存逻辑、校验逻辑或后端枚举。~喵

### 2026-04-23

- **糖尿病分级页和高血压分级页一样，`lastSf` 也必须走分级档案表口径**：
  - 这次 BMI 明明在 `MB_TNB_1ZHKZMB_ZS + MB_TNB_ZHKZMB_ITEM_ZS` 有历史，但页面没有“最近一次结果”和图表按钮。
  - 根因有两层：
    - 后端 `lastSf` 仍然查的是随访表 `MB_TNB_SFFW_1MAIN_ZS + MB_TNB_SFFW_3DATA`
    - 前端模板用 `x.field_code == item3.rid` 做匹配
  - 但糖尿病分级档案明细表真实保存的是：
    - `FIELD_CODE='BMI'/'KFXT'/'CHXT'/...`
  - 所以一旦页面口径切回分级档案表，模板和图表传参都必须同步改成：
    - `item3.dict_key`
  - 只改查询表，不改模板匹配，页面会继续表现成“数据库有值，但按钮不显示”。~喵

### 2026-04-22

- **高血压分级改成档案表口径后，模板匹配也必须从 `rid` 切到 `dict_key`**：
  - 这次高血压后端把 `lastSf` 和图表都切到了 `MB_GXY_1ZHKZMB_ZS + MB_GXY_1ZHKZMB_ITEM_ZS`。
  - 新链路返回的是：
    - `field_code = DICT_KEY`
  - 但模板最初仍然在用：
    - `x.field_code == item3.rid`
    - `showEchart(item3.rid, ...)`
  - 结果就是：
    - `lastSf` 有数据
    - 但页面整列不显示
    - 按钮看起来像“没接上”
  - 正确做法是同步改成：
    - `x.field_code == item3.dict_key`
    - `showEchart(item3.dict_key, ...)`
  - 只改后端口径、不改模板匹配，页面会表现成“数据有但按钮消失”。~喵

- **高血压分级页的“最近一次结果”和图表不能查随访表，要查分级档案表**：
  - 业务口径上，高血压分级页应对应：
    - 主表：`MB_GXY_1ZHKZMB_ZS`
    - 明细表：`MB_GXY_1ZHKZMB_ITEM_ZS`
  - 明细表不是 `FIELD_CODE`，而是：
    - `DICT_TYPE='MB_GXY_ZHKZMB'`
    - `DICT_KEY='XYG/XYD/...`
    - `VAL_TEXT`
  - 因此高血压分级页不能继续复用高血压随访接口 `/api/vab/mbGxySffw1mainZs/queryKzzbList`，否则会出现：
    - 随访表有多条
    - 分级档案表只有少量条目
    - 页面图表和分级表单本身不一致
  - 正确做法：
    - 在高血压分级控制器上新增自己的 `queryKzzbList`
    - 前端按钮点击传 `dict_key`
    - 后端按 `DICT_KEY` 查询 `MB_GXY_1ZHKZMB_ITEM_ZS` 历史。~喵

- **高血压分级的图表数据链路原本就是完整的，问题通常只是前端模板没把按钮和弹窗接出来**：
  - 高血压分级页已经会通过 `findKzzb('MB_GXY_ZHKZMB')` 拿到控制目标项和每项指标 `rid`。
  - 高血压后端也已经有自己的图表接口：
    - `/api/vab/mbGxySffw1mainZs/queryKzzbList`
  - 如果页面只有“最近一次结果”而没有图表按钮，不要先查数据库或后端，先看模板有没有把：
    - 图表按钮
    - 图表弹窗组件
    - `showEchart()` 调用
    接上。~喵
  - 这次正确做法是：
    - 在 `gxyFenjijibieForm.vue` 的“最近一次结果”列补 `tooltip + line-chart-line` 按钮
    - 新增高血压专用弹窗组件 `MbGxyZhkzmbEchart.vue`
    - 弹窗组件只调高血压自己的 `queryKzzbList`。~喵

### 2026-04-22

- **慢性肾病分级图表要查 ITEM 明细表，不是主表固定列**：
  - 慢性肾病分级当前控制目标项：
    - `EGFR_TARGET`
    - `UACR_TARGET`
    - `SBP_TARGET`
    - `DBP_TARGET`
    - `HBA1C_TARGET`
  - 这套值不是像慢阻肺那样存主表固定列，而是通过 `targetItemList` 机制落在 `MB_MXSB_ZHKZMB_ITEM_ZS` 中。
  - 因此慢性肾病专用 `queryKzzbList` 应该查：
    - 主表：`MB_MXSB_1ZHKZMB_ZS`
    - 明细表：`MB_MXSB_ZHKZMB_ITEM_ZS`
    - 关联：`a.RID = b.ZHUBIAO_FID`
  - 如果误按慢阻肺的主表列方式实现，会查不到任何控制目标历史。~喵

- **慢阻肺分级图表要查主表，不查 ITEM 明细表**：
  - 慢阻肺分级里这次要做图表的 5 项：
    - `catScore`
    - `mmrcScore`
    - `fev1Pred`
    - `fvc`
    - `jxzjCs`
  - 实际都保存在 `MB_MZF_1ZHKZMB_ZS` 主表固定列中，不是在 `MB_MZF_ZHKZMB_ITEM_ZS` 里。
  - 因此慢阻肺专用 `queryKzzbList` 不能照冠心病/脑卒中的 ITEM 表套路写，而应做：
    - `zbRid -> 主表列名` 映射
    - 返回 `suifang_rq / field_code / val_text`
  - 如果误查 `MB_MZF_ZHKZMB_ITEM_ZS`，图表会一直空白。~喵

- **慢阻肺表单可以先补“图表按钮状态”，但不要伪造图表数据链路**：
  - 这次慢阻肺分级表单已先按目标样式补上图表按钮，让“最近一次结果”列的交互状态和其他病种统一。
  - 但在未补齐慢阻肺专用图表弹窗组件与后端 `queryKzzbList` 之前，不应假装已经有可用图表。
  - 更稳妥的做法是：
    - 先补按钮视觉状态
    - 点击时给出“图表功能待接入”提示
    - 等后续专用数据链路打通后，再把点击逻辑切成真实图表弹窗。~喵

- **慢阻肺分级表单的五项控制目标可以先做“表格化收口”，不必一步到位接全历史逻辑**：
  - 这次慢阻肺分级表单里 `FEV1占预计值 / FVC / CAT评分 / mMRC评分 / 近一年急性加重次数` 原本是分散输入区。
  - 如果当前目标只是让界面结构对齐其他病种的“控制目标表格”风格，可以先统一收成一张表，列为：
    - 项目
    - 目标值
    - 最近一次结果
  - 不需要强行同时引入：
    - 趋势
    - 是否达标
    - 检测日期
  - 这样可以先把交互结构稳定下来，再决定后续是否接慢阻肺专用图表和历史值。~喵

### 2026-04-21

- **脑卒中图表弹窗组件也必须独立，不要只改数据查询函数**：
  - 这次脑卒中分级表单先补了专用 `queryKzzbList`，也把“最近一次结果”切到脑卒中自己的接口，但图表点击后仍然空白。
  - 真实原因是 `NzzFenjiForm.vue` 仍在引用糖尿病弹窗组件 `MbTnbZhkzmbEchart.vue`，组件内部请求写死是糖尿病 `/api/vab/mbTnbSffw1mainZs/queryKzzbList`。
  - 结论：
    - 只改表单里的 `loadLastKzzbVals()` 不够
    - 只要图表弹窗组件还是旧病种组件，按钮点击后就会继续查错表
  - 正确做法是为脑卒中新增自己的图表弹窗组件，并在表单里切换引用。~喵

- **脑卒中分级图表不能借高血压/糖尿病随访表**：
  - 这次脑卒中分级表单按钮出来后，点击仍然空白，根因不是前端样式，而是图表查询还在走高血压/糖尿病接口。
  - 但脑卒中分级保存时，`dynfield` 是直接落到：
    - `MB_NZZ_1ZHKZMB_ZS`
    - `MB_NZZ_ZHKZMB_ITEM_ZS`
  - 且保存字段编码就是：
    - `XY`
    - `XYG`
    - `XYD`
    - `XT`
    - `XZ`
  - 因此图表历史也必须回到脑卒中自己的表去查，而不是借 `mb_gxy_sffw_3data` 或 `mb_tnb_sffw_3data`。~喵
  - 最终做法：
    - 后端新增 `/api/vab/mbNzzSffw1mainZs/queryKzzbList`
    - 前端 `NzzFenjiForm.vue` 的 `loadLastKzzbVals()` 只调用脑卒中自己的 `queryKzzbList`
  - 只有这样，“脑卒中分级表单当前值 / 最近一次结果 / 图表历史”三者才来自同一条业务链路。~喵

- **脑卒中分级表单没有图表按钮时，先查后端是否真的返回了 `MB_NZZ_ZHKZMB` 配置**：
  - 这次脑卒中分级表单按钮完全不出现，不是样式没生效，而是 `findKzzb({ zbType: 'MB_NZZ_ZHKZMB' })` 根本拿不到配置。
  - 数据库中当前也没有 `SYS_DICT.DICT_TYPE='MB_NZZ_ZHKZMB'`，所以不能指望直接走字典表。
  - 解决方式是像冠心病一样，在后端 `MbField2tangVabApiCtrl.findKzzb()` 里显式补一套脑卒中专用配置，至少返回：
    - `XYG`
    - `XYD`
    - `XT`
    - `XZ`
  - 只要前端拿到这些项的 `rid`，按钮显示条件 `v-if="getItemChartRid(it)"` 才能成立。~喵

- **脑卒中图表按钮显示条件要和结果回显逻辑保持一致**：
  - 脑卒中分级表单里，`KZMB_ITEMS` 给 `XYG / XYD` 定义了 `legacyCode: 'XY'`。
  - `getItemLastValue()` 已经支持通过 `legacyCode` 回退算“最近一次结果”，但 `getItemChartRid()` 最初只查当前编码，不查 `legacyCode`。
  - 结果就会出现：
    - 最近一次结果能显示
    - 但图表按钮完全不出现
  - 修法不是改样式，而是让 `getItemChartRid()` 也按同样规则支持 `legacyCode` 回退，保证“按钮是否显示”和“结果是否可取到”遵循同一套判定。~喵

- **分级表单的图表按钮样式要跨病种统一**：
  - 冠心病和脑卒中分级表单里的“综合控制目标”图表按钮，都应直接对齐糖尿病现有样式。
  - 统一后的按钮状态：
    - `el-tooltip`
    - `el-button`
    - `vab-icon icon="line-chart-line"`
    - `plain + type="primary"`
    - `.jieguo_btn { font-size: 12px; border-radius: 5px; }`
  - 不建议每个病种各自维护一套“圆形加号 / 方形图标 / tooltip 有无”的变体，否则后续用户会感知成不同功能入口。~喵

- **同病种交互风格优先复用现有页面状态**：
  - 这次冠心病分级页的图表按钮虽然功能已通，但视觉状态与糖尿病分级不一致，导致用户第一眼会觉得“不是同一套系统控件”。
  - 如果同类病种页面已经存在成熟交互，优先直接对齐现有状态，而不是重新发明一个相近样式。
  - 本次最终对齐规则：
    - 使用 `el-tooltip + el-button + vab-icon(line-chart-line)` 组合
    - 保持 `plain + type=primary`
    - `jieguo_btn` 用 `font-size: 12px`、`border-radius: 5px`
  - 这样能把“这是查看图表的按钮”这个认知和糖尿病页面保持一致，减少学习成本。~喵

- **冠心病分级图表必须走冠心病专用链路**：
  - 冠心病分级页“综合控制目标”图表不要再借高血压或糖尿病接口兜底。
  - 更干净的做法是：
    - 控制目标配置由 `findKzzb('MB_GXB_ZHKZMB')` 直接返回冠心病自己的 7 个目标项
    - 历史曲线只查 `MB_GXB_1ZHKZMB_ZS + MB_GXB_ZHKZMB_ITEM_ZS`
  - 这样图表语义和当前分级档案完全一致，不会出现“冠心病页面画的是其他病种历史”的脏数据问题。~喵

- **Oracle 里不要用 `<> ''` 判断空字符串**：
  - Oracle 会把空字符串 `''` 当成 `NULL`。
  - 这次图表接口明明查 `MB_GXB_ZHKZMB_ITEM_ZS` 有数据，但 SQL 条件写成：
    - `nvl(trim(val_text), '') <> ''`
  - 结果在 Oracle 中仍然会把所有行过滤掉，接口表现为：
    - `success: true`
    - `data: []`
  - 正确写法应优先使用：
    - `trim(val_text) is not null`
  - 这个坑非常隐蔽，尤其容易在“数据库手工查有数据，但接口返回空数组”时误导排查方向。~喵

- **“接口 success 但图表空白”的排查顺序**：
  - 第一步先看浏览器 Network，不要先怀疑 ECharts。
  - 第二步确认请求体参数：
    - `jbxxRid`
    - `zbRid`
  - 第三步确认响应体里的 `data` 是不是真的空。
  - 第四步把后端 `runSql` 原样拿到数据库执行，验证是否是 SQL 条件问题。
  - 如果数据库直接执行有数据，而接口 `data: []`，优先怀疑：
    - Oracle `NULL`/空字符串判断
    - 应用实际连接库与手工查询库不一致
    - 返回字段别名与前端读取字段不一致
  - 本次最终根因就是第 4 步定位出来的 Oracle 空值判断问题。详见 `KONWLEDGE/2026-04-21-gxb-fenji-chart-debug.md`。~喵

### 2026-04-17

- **个人首页冠心病快捷新建入口复用规则**：
  - 冠心病个人首页里的快捷 `+` 入口，不要单独重写“找最后一次记录再组装新表单”的逻辑。
  - 应直接复用冠心病分级 Drawer 的既有新建入口：
    - `YsMbGxbFenjiDrawer.newFormData({ jbxxidid })`
    - Drawer 内部再调用 `GxbFenjiForm.doloadConfig(jbxxidid)`
  - `doloadConfig(jbxxidid)` 已内置“基于最后一次分级方案【新建】”语义：
    - 先调用 `findByJbxx({ jbxxid })` 读取末次分级
    - 再用 `fillData(rs, { mode: 'new' })` 预填表单
    - 同时清空 `rid`，避免误走编辑保存
  - 这样可以保证个人首页和分级列表页的新建行为完全一致，后续若新建预填规则调整，只需要改表单内部一处。~喵

### 2026-04-03

- **在管患者列表随访信息的用户姓名兜底规则**：
  - `MbJbxx02infoZs_Mapper.xml` 中的 `gxy_sf_msg`、`tnb_sf_msg` 以及其他病种同类“随访信息”字段，不要直接拼 `f_get_user_xm(create_user_fid)`。
  - 历史随访数据里可能存在 `CREATE_USER_FID` 仍有值，但 `sys_user` 已不存在对应 `RID` 的情况；此时数据库函数可能抛出 `ORA-01403: 未找到任何数据`，并被直接显示到前端列表。
  - 更稳妥的写法是：
    - `nvl((select u.xingming from sys_user u where u.rid = s.create_user_fid and rownum = 1), s.create_user_fid)`
  - 这样查到用户时显示姓名，查不到时降级显示原始用户编号，避免异常泄漏到 UI。~喵

### 2026-03-31 12:10:00

- **个人首页病种 URL 尾缀约定**：
  - 个人首页支持通过查询参数直接指定默认病种，格式为：
    - `#/hzinfo/personhome/:jbxxidid?disease=TNB`
    - `#/hzinfo/personhome/:jbxxidid?disease=GXY`
    - `#/hzinfo/personhome/:jbxxidid?disease=GXB`
    - `#/hzinfo/personhome/:jbxxidid?disease=NZZ`
    - `#/hzinfo/personhome/:jbxxidid?disease=MZF`
    - `#/hzinfo/personhome/:jbxxidid?disease=MXSB`
  - 进入页面或浏览器刷新时，壳层 `src/views/tnb/personhome/index.vue` 应优先读取 `route.query.disease`，并按 `diseaseConfig[code].field` 检查该患者是否已纳入对应病种管理。
  - 只有当查询参数合法且该病种在管时，才使用该病种作为默认展示；否则回退到“第一个已纳入管理的疾病”。
  - 病种切换后应同步 `router.replace({ query: { ...route.query, disease: code } })`，这样刷新、复制链接和浏览器前进后退都会保持当前病种，不要只改本地 `currentDisease`。~喵

### 2026-03-27

- **个人首页控制目标多病种编辑模式**：
  - 控制目标组件的编辑按钮按病种分发到不同 Drawer：
    - TNB: `YsMbTnbZhkzmbZsDrawer`，API 为 `tnbZhkzmbApi.get` + `findByJbxx11`
    - GXY: `YsMbGxy1zhkzmbZsDrawer`，API 为 `gxyZhkzmbApi.get` + `findByJbxx`(即 `findLastByJbxx`)
  - 两个 Drawer 接口一致：`show({ rid })` 打开已有记录，`newFormData({ jbxxidid })` 新建
  - 其他病种(GXB/NZZ/MZF/MXSB)暂无 Drawer，跳转对应分级页面 `/hzinfo/xxxfenji/:jbxxidid`

### 2026-03-26

- **转诊/会诊数据接口**：
  - API 地址：`/api/vab/mbTnbTodoZhuanzhenLc/getList`（POST，JpaSearchForm 格式）
  - 查询参数：`{ mbJbxxFid, pageNo, pageSize, orderStr: 'rid|desc' }`
  - 返回结构：`res.data.content` 或 `res.data.list`（数组）
  - 关键字段：`renwuZtCh`(任务状态中文)、`shenqingJigouMc`(申请机构名称)、`shenqingYs`(申请医生)、`zhuanruJigouMc`(转入机构名称)、`zhuanruJigouBm`(转入机构编码)、`huanzheXingming`(患者姓名)、`beizhu`(备注)、`createTime`(创建时间)
  - 注意：老版本 beetl 个人首页（`01tnb_index.beetl` + `01mbtnb.js`）的转诊部分只有空 HTML 结构，JS 中没有实现数据加载，实际逻辑参考 Vue2 独立页面 `Ys_MbTnbTodoZhuanzhenLcOneJbxxIndex.vue`
  - 另有一个 `Ls`（历史）接口 `/api/vab/mbTnbTodoZhuanzhenLs/getList`，以及 `getZhuanzhenMq`（`/api/vab/mbZhuanzhen/getZhuanzhenMq`），后者后端可能未实现，不建议使用

- **诊疗记录数据接口（MPI主索引）**：
  - API 地址：`/api/vab/mbJbxx02infoZs/fetchmpi`（GET）
  - 查询参数：`{ daid: 档案ID, _pageNo: 1, _pageSize: 4 }`
  - 返回结构：`res.data.content` 或 `res.data.list`（数组），另有 `res.data.viewfull`（电子档案浏览器链接）
  - 关键字段：`laiyuan`(来源)、`yymc`(就诊机构)、`keshi`(科室)、`yisheng`(医生)、`zhenduan`(诊断)、`rq`(就诊时间)
  - 注意：此接口调用的是 MPI（主患者索引）平台，可能依赖外部系统对接，如果后端未配置 MPI 对接则会返回错误提示

### 2026-03-19

- <span style="color:#f2d8f0">**登录链路关键结论（非常重要）**：当前 Vue3 项目打包部署到后端后，`/framework/login/ajaxCLogin` **不要默认走共享 `src/utils/request.ts` / axios 实例链路**。本次已实测确认：同一组账号密码、同一后端、同一 `emptykey`，用浏览器内共享请求链路会返回“帐户或密码不正确”，但改为原生 `fetch` 直连提交后即可正常登录。</span>
- <span style="color:#f2d8f0">**判断依据**：Vue3 浏览器端生成的 `emptykey`，原样复制后直接 POST 到 `http://127.0.0.1:8081/ygtmb/framework/login/ajaxCLogin`，后端可以成功登录；说明问题不在 RSA 算法本身，也不在后端账号密码，而在浏览器内这条登录请求经过共享请求封装后的实际行为差异。</span>
- <span style="color:#f2d8f0">**落地规则**：登录接口 `ajaxCLogin` 应在 `src/api/user.ts` 中使用原生 `fetch` 直连，显式指定 `credentials: 'include'`、`Content-Type: 'application/json;charset=UTF-8'`，请求体保持 `{"emptykey":"..."}`。不要轻易再并回共享 `request.ts`，除非先完成对比验证。</span>
- 调试 `ajaxCLogin` 时，`encryptedData--self-check=> null` 或本地私钥解密失败 **不能直接认定前端加密错误**。本次排查确认，前端调试里写的旧 `privateKey` 与后端当前 `configV2.3.5.properties` 中实际启用的 `RSA_KEY_PRIVATE` 不是同一把；这种情况下本地回验失败没有诊断价值。
- 后端真实登录解密入口在 `ygt_manbing_java_ef4/manbing-core/src/main/java/com/ccesun/mb/web/ctrl/vab/login/LoginCtrl.java` 的 `doLogin()`：读取 `emptykey` 后直接执行 `RSAUtils.Decrypt.byPrivateKey(rsa)`，解出完整 JSON，再取 `username/password`。
- 后端真实 RSA 密钥来源在 `ygt_manbing_java_ef4/manbing-bootstrap/src/main/resources/configV2.3.5.properties`，启动时由 `SpringBootStarter.java` 执行 `RSAUtils.setup(pubKey, priKey)` 注入。调试前端 RSA 时，必须以这里的当前密钥为准。
- <span style="color:#f2d8f0">**服务器部署配置规则（非常重要）**：生产/服务器环境下，`window.gl.url` 优先建议配置为相对路径（例如 `/ygtmb`），不要轻易写成 `https://ip:port/ygtmb` 这类绝对地址。若绝对地址证书无效、跨域策略不一致或代理链路不同，登录前的 `/api/ras/publickey` 就会先失败，表现为 `publicKey` 读取报错、无法登录。</span>
- <span style="color:#f2d8f0">**本次服务器故障现象**：页面实际访问地址是 `http://192.168.125.42:5555/ygtmb/webjars/dist/index.html`，但服务器 `configV2.3.5.properties` 中的 `config.js_url` 被配置成了 `https://192.168.125.104:8448/ygtmb`，导致浏览器先请求 `https://192.168.125.104:8448/ygtmb/api/ras/publickey`，并报 `net::ERR_CERT_AUTHORITY_INVALID`，随后前端报 `Cannot read properties of undefined (reading 'publicKey')`。</span>
- <span style="color:#f2d8f0">**本次服务器修复方法**：修改服务器 `configV2.3.5.properties` 中的 `config.js_url`，将绝对地址改为 `/ygtmb`，保存后重启后端服务。验证方式：浏览器控制台执行 `window.gl`，应看到 `url: '/ygtmb'`。</span>

### 2026-03-18

- **API 数据来源注意事项**：
  - `getinfo({ rid })` API（`/api/vab/mbJbxx02infoZs/get`）**不返回** `zhkhbm` 字段，该字段只在列表查询（如 `queryTNB`/`queryGXY`）中填充
  - 个人首页等使用 `getinfo` 的页面，需要各疾病 `zhkhbm` 数据时应单独调用对应 `findByJbxx` API：
    - 糖尿病：`findByJbxx11({ jbxxid })`（`/api/vab/mbTnb1zhkzmbZs/findByJbxx`）
    - 高血压：`findByJbxx({ jbxxid })`（`/api/vab/mbGxy1zhkzmbZs/findLastByJbxx`）
    - 冠心病：`findByJbxx({ jbxxid })`（`/api/vab/mbGxb1zhkzmbZs/findByJbxx`）
    - 脑卒中：`findByJbxx({ jbxxid })`（`/api/vab/mbNzz1zhkzmbZs/findByJbxx`）
    - 慢阻肺：`findByJbxx({ jbxxid })`（`/api/vab/mbMzf1zhkzmbZs/findByJbxx`）
    - 慢性肾病：`findByJbxx({ jbxxid })`（`/api/vab/mbMxsb1zhkzmbZs/findByJbxx`）
  - 修改后端通用 API 前，务必先用 `grep` 统计受影响的前端调用点，避免影响其他页面
  - 各疾病 API 路径命名不一致，需注意：高血压使用 `findLastByJbxx`，其他疾病使用 `findByJbxx`

- **各疾病 API 返回数据结构差异**：
  - 糖尿病 API：`findByJbxx11` 返回数据包含 `rid`、`guanliFenleiCh`（管理分类中文）、`fanghuFenjiCh`（防护分级中文）等字段
  - 高血压 API：`findLastByJbxx` 返回数据包含 `rid`、`xxgFxFenceng`（心血管风险分层）、`xxgFxFenqi`（心血管风险分期）等字段
  - 冠心病 API：`findByJbxx` 返回数据包含 `rid`，但主要业务字段在 `dynfield` 对象中：`GXB_ICD`（基本分类，JSON 数组）、`guanliFenlei`（管理分类 M2，拼音值）、`xxgwxysfcResult`（预防分级 M3，值：'1'/'2'/'3'）
  - **注意**：不同疾病的 API 返回数据结构差异很大，字段层级和命名都不一致，需要分别处理

- **编辑按钮行为**：与原后端渲染版本保持一致，使用 Element Plus Dialog 弹窗打开编辑页面
  - 通过 iframe 加载 `/hzinfo/jbxxeditor/{jbxxidid}?Authorization={token}`
  - 弹窗标题格式："编辑档案 {患者姓名}"
  - 弹窗尺寸：宽度90%，高度自适应（最小600px），关闭时销毁 iframe 避免内存泄漏

- **左侧菜单"个人首页"弹窗行为**：在档案编辑页面左侧菜单中，点击"个人首页"改为在当前页面弹出 Dialog 弹窗
  - 使用 Element Plus Dialog 组件，通过 iframe 加载个人首页 `#/hzinfo/personhome/{jbxxidid}`
  - 弹窗标题格式："{患者姓名} - 个人首页"
  - 弹窗尺寸：宽度95%，高度自适应（最小700px），关闭时销毁 iframe
  - 替代原有的 `window.open` 新标签页打开方式，提升用户体验

- 个人首页组件架构（壳层 + 疾病视图分层设计）：
  - **URL 结构**：`/hzinfo/personhome/:jbxxidid`，只包含档案 ID，不包含疾病类型
  - **壳层** (`index.vue`)：
    - 负责加载患者基础信息 (`getinfo` API)
    - 从 `da05dqglxxObj` 读取患者已纳入管理的疾病列表
    - 维护 `currentDisease` 状态（TNB/GXY/GXB/NZZ/MZF/MXSB）
    - 自动选择第一个管理的疾病作为默认值
    - 通过 `<component :is="currentDisease + 'View'">` 动态渲染对应疾病视图
  - **共享头部** (`PatientHeader.vue`)：
    - 显示患者姓名、性别、年龄、证件号等基础信息
    - 显示患者已纳入管理的疾病切换按钮组
    - 编辑按钮（使用 Dialog 弹窗 + iframe 加载编辑页面）
  - **疾病视图** (`components/diseases/*.vue`)：
    - 每种疾病一个独立视图组件，如 `TnbView.vue`、`GxyView.vue`
    - 视图内部组合引用各业务板块组件（RefinedManagement、ControlTargets 等）
    - 通过 `currentDisease` prop 判断应显示哪种疾病的数据
  - **业务组件** (`components/*.vue`)：
    - 精细化管理、控制目标、治疗方案、随访记录等
    - 接收 `currentDisease` 和 `jbxxidid` props
    - 根据当前疾病调用对应 API 获取数据
  - **优势**：
    - 各疾病视图独立维护，互不影响
    - 新增疾病只需创建新的 View 组件和对应的 API 调用逻辑
    - 业务组件复用，通过 `currentDisease` 区分数据

- 个人首页各板块数据来源梳理（按疾病区分）：
  - **疾病管理标签**（显示在头部）：
    - 从 `jbxx.da05dqglxxObj`（`MB_JBXX_05DQGLXX` 表）读取纳入状态字段
    - `isTnb`（糖尿病）、`isGxy`（高血压）、`isGxb`（冠心病）、`isNzz`（脑卒中）、`isMzf`（慢阻肺）、`isMxsb`（慢性肾病），值为 `'1'` 表示已纳入管理
  - **精细化管理** (`RefinedManagement.vue`)：
    - 根据 `currentDisease` 调用对应 `findByJbxx` API 获取数据
    - 糖尿病：疾病分类、管理分类、预防分级、心血管危险因素分层
    - 高血压：高血压分类、风险分层(`xxgFxFencengCh`)、分期(`xxgFxFenqiCh`)、防护分级
    - 冠心病：基本分类(`dynfield.GXB_ICD`→ICD编码映射)、管理分类M2(`dynfield.guanliFenlei`→拼音映射)、心血管危险因素分层(`xxgwxysfcResult`→数字映射)
  - **控制目标** (`ControlTargets.vue`)：
    - 根据 `currentDisease` 调用对应 `findByJbxx` API 获取最新控制目标
    - 数据从 `zhkhbm.dynfield` 读取：KFXT（空腹血糖）、CH2H（餐后2H）、THXHDB（糖化血红蛋白）、XY_D/G（血压）、LDL_C/HDL_C/TG/TC（血脂）、BMI 等
    - 不同疾病达标标准不同（如 LDL-C：糖尿病 <2.6，冠心病 <1.8）
    - **趋势分析**：调用 `/getList` 接口获取最近2条历史记录（按 `createTime` 降序），对比当前值与上一次值，显示上升（↑）/下降（↓）趋势箭头
  - **治疗方案** (`TreatmentPlan.vue`)：
    - 调用 `findLastInputByJbxxFid({ jbxxidid })` 获取最近一次诊疗记录
    - 用药信息在 `fffcfg.dynfield` 中，格式：`{ 药品编码: { ypmc, gg, yf, pc, jl } }`
  - **随访记录** (`FollowUpRecords.vue`)：
    - 调用 `fetchMenu({ danganid })`，随访列表在 `data.sffwList` 中
    - 如果病种已存在 `...Sffw1mainZsDanganIndex.vue` 这类档案内列表组件，个人首页应优先在 `Element Plus Dialog` 中直接挂载该列表组件，而不是 iframe 打开整页
    - 行内“详细”可直接复用列表组件自身的 `doView(row)`，这样会继续沿用原有抽屉/表单链路，避免重复造详情弹窗
    - 当前已确认可直接复用的病种：糖尿病、高血压；其他病种需要先补齐档案内列表组件，再接入个人首页弹窗
  - **转诊/会诊** (`ReferralRecords.vue`)：
    - 调用 `getZhuanzhenMq({ jbxxFid })` 获取最近一次转诊
  - **诊疗记录** (`MedicalRecords.vue`)：
    - 从随访记录中解析，就诊机构 `suifangJg`，就诊时间 `suifangRq`
- Vue3 `<script setup>` 中调用后端 API 流程：先 `import { xxx } from '@/api/...'` 导入具体方法，然后在 `onMounted` 或 `watch` 中异步调用，使用 `ref()` 存储响应数据，使用 `computed()` 派生展示数据
- 列表类组件（随访/转诊/诊疗）统一使用 `el-table` + `el-empty` 组合，通过 `v-loading` 控制加载状态，空数据时友好提示”暂无XX记录”

### 2026-03-16 00:00:00

- `MB_JBXX_05DQGLXX` 这类”纳入管理”状态表里，`*_CREATE_TIME` 如果数据库列是 Oracle `DATE`，Java 实体和 Controller 都必须保持 `Date` 语义，不要把 `DateEx.format(...)` 后的字符串直接传给 MyBatis `#{...}`。
- 这次“纳入冠心病管理”报 `ORA-01861` 的根因就是：`IS_GXB_CREATE_TIME` 在 Java 中被错误声明成了 `String/VARCHAR`，而 SQL `mergeData_as_gxb` 又直接把该字符串写进 `DATE` 列。`IS_NZZ_CREATE_TIME / IS_MZF_CREATE_TIME / IS_MXSB_CREATE_TIME` 同类字段也要一起排查并统一修正。
- Vue CLI / webpack-dev-server 下如果只在 `src/main.ts` 里过滤 `window.onerror`、`unhandledrejection` 和 `app.config.errorHandler`，仍然可能挡不住开发环境的全屏红色 overlay；`ResizeObserver loop completed with undelivered notifications` 这类已知无害错误要同时在 `vue.config.js -> devServer.client.overlay.runtimeErrors` 里过滤，才能避免切 tab 时被覆盖层打断。
- 表格里如果左列是 6 行固定病种状态，右列操作按钮不要再用多个 `<br />` 叠空行来“目测对齐”；更稳的方式是把右列改成同顺序的固定高度行容器，每个按钮各占一行，这样某一病种按钮隐藏时，其余病种仍能和左侧标签逐行对应。
- 如果同一“操作”列里还放了“编辑基本信息”之类的通用链接，要避免把它放在按钮列表前面参与正常文档流；否则它会把整组病种按钮整体往下推，造成看起来“按钮和病种名错一行”。更稳的处理是把通用链接放到按钮列表后面，或单独做绝对定位。 
- 如果某列表左侧状态列已经使用 `el-descriptions` 做多行布局，右侧操作列要追求逐行严格对齐时，最稳的是直接复用同样的 `el-descriptions` 结构来承载按钮，而不是继续用 `div + min-height` 人工凑行高。 

### 2026-03-05 10:54:36

- 梳理并补充”动态路由/菜单、动态表单(dynfield)、Vue2→Vue3 运行时差异”的排查与修复要点，作为后续迁移的基线参考。

### 2026-03-05 15:43:28

- 迁移”冠心病服务”到 `hzinfo` 左侧菜单：新增路由映射 `gxbfj/gxbzlfa/gxbsffw`，并在 `fetchMenu` 的结果里补齐 `userMenuDataML['gxbsffw']` 默认数组，避免菜单渲染时报空。
- 冠心病随访页面使用 iframe 打开动态表单：固定入口 `/formdesign/display/253141810000185?callparent=on`，并同时传 `RIDC=253141810000185` 与业务参数（新建/编辑都兼容 `RID/rid`、`DAID/daid`、`HZID/hzid`）。
- 冠心病随访字典对照：随访方式 `mbSF_guanXB_suiFangFangShi`、症状 `mbSF_guanXB_zhengZhuang`（注意返回可能是 array 或 object，需要统一归一化成 `value->label` map）。

### 2026-03-05 16:23:08

- Vue3 下不再需要（也不支持）`this.$set`：对 `data()` 返回的对象（如 `fwbDetailByRid`、`form.dynfield`）直接赋值即可触发响应式更新。
- Watch 监听对象字段时要写全路径：例如 `xxgwxysfcResult` 应监听为 `form.xxgwxysfcResult`，否则联动逻辑（如”精细化管理方案”随 M2/M3 变化自动校验）不会触发。
- 冠心病分级（人员分级级别）表单里多选字段（如 `dynfield.GXB_ICD`、家族史）建议保存为 JSON 数组字符串，回填时同时兼容 JSON 字符串与逗号分隔两种历史数据形态。

### 2026-03-05 17:20:18

- Element Plus 的 `el-date-picker` 使用 dayjs 格式 token：应使用 `YYYY-MM-DD`（大写），不要沿用 Element UI 常见的 `yyyy-MM-dd`。否则会出现占位符/显示值异常（例如 `yyyy-02-日`，其中 `dd` 会被当作”星期”渲染成”日”）。

### 2026-03-05 18:05:35

- `IframeDialog`（`src/views/system2/linkIframe/open_iframe_by_dialog.vue`）对外只提供 `show(url, title, other)` / `showBySimple(...)`，不要调用不存在的 `getLink(...)`。业务侧需要先用 `makeUrl(url, params)` 把参数拼成完整 URL，再 `this.$refs.iframedialog.show(url, title)` 打开弹窗，否则点击按钮会”无反应”（实际是运行时报错）。

### 2026-03-05 18:15:07

- Element Plus 的 `el-dialog` 在本项目里应使用 `v-model=”visible”`（绑定 `modelValue`），不要写 `v-model:visible`。否则会出现：业务侧 `show()` 已执行且 Console 打印 `this.link=>...`，但弹窗仍不显示。

### 2026-03-06 00:00:00

- fromcreat 表单 iframe 打开链路要沿用 Vue2 的 `src/views/system2/linkIframe/dialog.vue`：业务页调用 `this.$refs.iframedialog.getLink('/formdesign/display/...', params)`，由该组件内部自动补 `${baseURL}`、token、时间戳和 `dialog=on`。如果改成 `open_iframe_by_dialog.vue` 并直接传相对路径，会落到前端 dev server，出现 `Cannot GET /formdesign/display/...`。

### 2026-03-09 00:00:00

- 如果某个慢病页面希望”像随访一样”打开后端 form-create 表单，就不要继续走本地 `form-create` 运行组件。应直接复用 `src/views/system2/linkIframe/dialog.vue`，并以 `getLink('/formdesign/display/<RIDC>?callparent=on', { RIDC, rid/RID, daid/DAID, hzid/HZID })` 的方式打开。这样打开链路与随访一致，保存后可直接依赖 `fetch-data` 事件刷新列表。
- 已切换为 iframe 打开的历史本地表单组件如果仍留在 `src/views/**` 下，webpack 仍可能在路由扫描时尝试编译它。此时不要保留失效的 JSON/`form-create` 导入，最稳妥的做法是把该组件收敛为占位组件，避免因无效资源路径导致整站编译失败。
- `mbscGxb` 旧列表接口目前会在治疗方案页触发后端 SQL 报错 `ORA-00904: “T”.”SHFE_ID”`。在没有确认正确后端列表接口前，治疗方案页不要自动加载这条旧列表；先保留 iframe 表单入口，避免页面一进入就被后端错误中断。

### 2026-03-09 11:00:00

- form-create 表单后端 API 开发模式：参考 `MbGxbSffw1mainZsVabApiCtrl`，使用 `SqlSessionTemplate` 直接操作 MyBatis Mapper，无需定义 Domain/Service，适合简单的 CRUD 场景。
- 表结构设计：`MBSC_JBXX`（主表）+ `MBSC_GXB`（从表），主从通过 `RID` 关联，主表存通用信息（姓名、身份证号、筛查日期等），从表存疾病特有字段。
- 前端 API 调用：新建 `src/api/mbsc/MbscGxb_api.ts`，导出 `getList/get/save/remove` 方法，业务页直接调用。
- 列表页字段映射：后端返回 `scRq/scJg/scYsName`，前端表格列使用 `dateFormat` 格式化日期显示。

### 2026-03-13

- 新增慢病类型（如冠心病）在管患者名单开发套路：
  1. **数据库层**：在 `MB_JBXX_05DQGLXX` 表新增 `IS_{DISEASE}`、`IS_{DISEASE}_CREATE_TIME`、`IS_{DISEASE}_CREATE_USER_FID` 三个字段，用于标记该疾病类型的在管状态。
  2. **Mapper XML 层**：
     - `MbJbxx05dqglxx_Mapper.xml` / `MbJbxx05dqglxxLs_Mapper.xml`：添加查询条件、MERGE 语句字段映射。
     - `MbJbxx02infoZs_Mapper.xml`：添加 `searchMbJbxx02infoZs{ Disease }` SQL，关联 `MB_JBXX_02INFO_ZS` 和 `MB_JBXX_05DQGLXX` 表，筛选 `IS_{DISEASE}='1'`。
  3. **后端 Service 层**：
     - `FastMbJbxx02infoZsService` 接口新增 `query{Disease}`、`query{Disease}NOT` 方法。
     - `FastMbJbxx02infoZsServiceImpl` 注入对应疾病的 `ZhkzmbService`（如 `FastMbGxb1zhkzmbZsService`），在查询方法中回填综合控制目标（`zhkhbm`）。
  4. **后端 Controller 层**：新建 `MbJbxx02infoZs{Disease}VabApiCtrl`，提供 `/api/vab/{prefix}jbxxzs/getList` 等接口，参考高血压/糖尿病 Controller 复制修改。
  5. **前端页面层**：
     - API 文件：`src/api/{module}/Mb{Disease}InfoZs_api.ts`，定义 `URL_QUERY` 指向 `/api/vab/{prefix}jbxxzs/getList`。
     - 列表页：复制高血压/糖尿病页面，修改字典类型（如 `MB_GXB_ZHONGLEI`、`MB_GXB_FENLEI`）、列显示（档案信息中增加疾病标识）、操作链接（分级配置、随访入口）。

- 注意事项：
  - 字典类型命名规范：`MB_{DISEASE}_ZHONGLEI`（种类）、`MB_{DISEASE}_FENLEI`（分类）、`MB_{DISEASE}_CQSF_FJGL`（分级管理）、`MB_{DISEASE}_ZHKZMB`（综合控制目标）。
  - 前端页面路径如果由后端菜单配置决定，需在 `src/views/gaoxueya/` 下创建适配文件（如 `gxbJbxx/Ys_MbJbxx02infoZsIndex.vue`），实际逻辑复用 `src/views/gxb/` 下的组件。
- 列表页字段映射：后端返回 `scRq/scJg/scYsName`，前端表格列使用 `dateFormat` 格式化日期显示。

### 2026-03-09 14:00:00

- form-create 表单提交无响应排查：检查 `rule.json` 中的 `submit.action` 是否指向正确的后端 API 地址（默认可能是示例地址 `/theexampleapi/submitFormDataInfo`）。
- 表单字段与后端对齐：使用 `grep '”field”:' rule.json` 提取所有字段名，确保 Controller 和 Mapper 都支持这些字段。
- 主从表设计：MBSC_JBXX（主表，存通用信息）+ MBSC_GXB（从表，存疾病特有检查指标），通过 RID 关联。

## 1. 项目结构与启动

- 项目类型：`admin-plus`（Vue3 + Webpack5 + TypeScript + Element Plus）
- 入口与主要目录：
  - 前端业务：`src/views/**`
  - 路由/权限：`src/router/**`、`src/store/modules/routes.ts`
  - 请求封装：`src/utils/request.ts`
  - CRUD 组件：`src/components/Crud/**`
  - VAB 组件库：`library/**`

常用命令（以 `package.json` 为准）：

- 安装依赖：`pnpm i`
- 启动开发：`pnpm run dev`
- 构建：`pnpm run build`

## 2. 前后端对接关键规则（最容易踩坑）

### 2.1 baseURL / 代理 / Cookie(Session)

本项目使用同一后端（Vue2 / Vue3 共用），但 **Cookie/Session 是否能携带** 会导致：

- 登录成功但 `getUserInfo` 失败（页面提示“无法取得用户信息”）
- 路由/菜单接口能返回，但后续接口 401/500

解决思路：

- 开发环境通过 `vue.config.js` 的代理把 `/ygtmb` 指向后端（例：`http://127.0.0.1:8081`）
- 前端请求统一走同源（`localhost:7788`）→ 代理转发，避免跨域导致 Cookie 丢失

相关文件：

- `vue.config.js`
- `src/config/net.config.js`

### 2.2 Axios 返回值形状（非常关键）

`src/utils/request.ts` 在响应拦截里 **直接 `return data`**（业务包裹对象），不是 `AxiosResponse`。

因此：

- 调用方拿到的是 `{ success, code, data, ext }` 这一层
- 不能按 `AxiosResponse` 解构 `response.ext`（会 TS 报错）
- 需要从业务对象的 `data.ext` / `ext` 位置取值，具体看接口返回

相关文件：

- `src/utils/request.ts`
- `src/store/modules/routes.ts`（读取 `jsTime` 时用 `data.ext`）

### 2.3 Authorization 头规范化

项目里做了 `normalizeBearerToken(token)`，用于：

- 避免重复 `Bearer Bearer xxx`
- 兼容 `token` 非 string 的情况（TS & 运行时）

相关文件：

- `src/utils/request.ts`

## 3. 动态路由（Vue2 没问题，Vue3 经常 404 的根因）

### 3.1 后端下发路由的 `component` 是 string

后端路由数据里的 `component` 常见值：

- `Layout`
- `@views/system2/foldKeepAlive/empty`
- `@views/tnb/.../SomePage`

在 Vue Router 4（Vue3）里，`RouteRecordRaw.component` 期望是组件/异步组件函数，而不是 string。

所以要做两层处理：

1. **接收原始后端路由**时，不要用 `VabRouteRecord`（component 类型不匹配）。
2. 把后端的 string 组件路径 **转换** 为真正可加载的组件（动态 import / Layout 映射）。

相关文件：

- `src/store/modules/routes.ts`
- `src/utils/routes.ts`（`convertRouter` 等）

### 3.2 为什么第一次点菜单“左侧第二列空白”，第二次又正常？

VAB 的 `column/comprehensive` 布局会用 `routesStore.tab.data` 去渲染第二列菜单。

首次进入/刷新时，如果：

- 动态路由刚注入，`tab.data` 仍为空或指向不存在的一级路由
- 或者“当前路由”还没来得及同步到 tab（例如路由守卫里先 `next()`，后异步写 tab）

就会出现：

- 第一次点菜单：路由能跳转（甚至到 404），但左侧第二列为空/不更新
- 第二次点菜单：由于 tab 已被补齐（或缓存命中），第二列恢复正常

排查/修复思路（优先顺序）：

1. **确认路由注入时机**：`routesStore.setRoutes()` 完成后再进入业务路由（或至少保证 tab 初始化已完成）。
2. **确认 tab 的“当前一级菜单”同步逻辑**：通常在 `router.afterEach` / 菜单点击回调里更新 `routesStore.tab`.
3. **确认后端路由的 `name` 与组件 `export default { name }` 一致**：不一致会导致缓存/KeepAlive、tab 命中失败，表象上像“第二列不刷新”。
4. **调试手段**：在开发环境把 `router` 挂到 `window` 上（例如 `window.__router = router`），在 Console 里观察每次点击的 `to.fullPath / matched`，并对比 `routesStore.tab.data` 是否变化。

### 3.3 “后端菜单有了但仍 404”的常见原因

- **父级节点本身没有实际页面**：Vue2 里父级可能只是一个“空壳容器”，Vue3 也需要一个真实可渲染组件（例如 `@views/system2/foldKeepAlive/empty`）。
- **component 字符串到真实组件的转换不一致**：Vue2 的 `require`/字符串解析，在 Vue3 需要统一走 `convertRouter()` 做动态 `import()`。
- **路径拼接规则不同**：后端 `path` 有的带 `/` 有的不带，子路由 `path` 可能是相对路径（`tnbhzgl`）也可能是绝对路径（`/ogtt`）。转换路由时要保持与 Vue2 相同的拼接语义。

## 4. 动态表单（dynfield / groupcodes）——“看起来保存成功但回填为空”的根因

后端很多业务表单并不是固定字段，而是：

- 主表字段（如 `suifangRq`、`suifangFs`、`tizhong1` 等）
- 动态字段：`dynfield`（以 `field_code` 为 key 的 KV）
- 动态字段勾选状态：常见形态：
  - `is_<field_code>`：`"1"`/`"0"`（字符串）
  - `group_<group_code>_is`：`"1"`/`"0"`
  - `groupcodes`：启用的 group 列表

因此“能显示但保存/回填不一致”通常来自：

- 前端只保存了 `dynfield`，没同步 `is_*` 或 `group_*_is`
- 或者把 `"1"/"0"` 当成 boolean/number 处理，导致后端解析失败/忽略

排查要点：

- 看 `fetchField` 返回的字段结构（如 `kzzb` / `other`），确认 field_code 与 group_code。
- 看编辑接口 `get` 返回的 `dynfield`、`groupcodes`、`is_*` 是否存在且类型符合预期（多数是字符串）。
- 保存前打 log：最终提交 payload 中是否包含这些 key（尤其是 `groupcodes` 与 `group_*_is`）。

相关文件（典型）：

- `src/views/gxy/Suifang/components/MbGxySffw1mainZsForm.vue`
- `src/views/tnb/Suifang/components/MbTnbSffw1mainZsForm.vue`
- `src/utils/kit/yesdict.ts`（字典与动态字段辅助）

## 5. Vue2 → Vue3 运行时差异（最容易踩的几类）

### 5.1 `this.$alert is not a function`

Vue2 + ElementUI 常用 `this.$alert / this.$confirm`。
Vue3 + Element Plus 需要改为 `ElMessageBox.alert/confirm` 或在全局显式挂载对应方法。

如果不改，表单校验/保存失败时抛错，会表现为：

- 弹出红屏 runtime error
- 表单一直转圈（loading 未被 finally 关闭）

### 5.2 “required 校验 + 0 值”被当成空

很多字段（运动频率/主食等）合法值可能是 `0`。
如果校验逻辑用的是“truthy 判断”或把 `"0"` 处理成空，会出现：

- 输入框里看得到 `0`
- 仍提示“请输入/请填写…”

处理原则：

- 校验时用“是否为 `null/undefined/''`”判断，不要用 `!value`。
- 回填时把后端返回的数字字符串（如 `"0"`）转换为 number（`0`），并确保输入组件使用 `v-model.number`（或手动 `Number(...)`）。

## 6. TypeScript 编译错误（迁移早期高频）

### 6.1 `RouteRecordRaw.component` 不能是 string

错误示例：`Type 'string' is not assignable to type 'RawRouteComponent'`。

原因：后端给 `component: 'Layout' / '@views/...'`，TS 认为必须是组件/异步组件。
处理：使用“后端原始路由类型（component 为 string）”接收，再通过 `convertRouter` 转换为可加载组件。

### 6.2 `AxiosResponse` 解构不到 `ext`

如果 `src/utils/request.ts` 响应拦截 `return data`（业务对象），调用方就不能把它当成 `AxiosResponse` 解构。

处理：统一按业务返回结构访问（`result.ext` / `result.data`，以实际接口返回为准）。

## 7. 推荐排查套路（适用于 404 / 回填为空 / 保存“假成功”）

1. Network：对比 Vue2 与 Vue3 同一操作的接口与 payload（URL、method、body）。
2. 看接口 `get` 返回：是否包含用于回填的 `dynfield`/`groupcodes`/`is_*` 等“影子字段”。
3. Vue DevTools：定位组件里的 `form`（或 `crud`）对象，确认类型（string/number/boolean）与预期一致。
4. Console：在提交前打印最终 payload；在提交后打印接口返回与后端提示（很多报错信息被包装在 `message/msg` 字段里）。

## 8. form-create / FcDesigner 接入

### 8.1 当前 Vue3 项目接新版 form-create 的基线

- 当前项目如果要对齐 `from_exampel_progect` 的新版 form-create，不需要先加示例页；正式接入的最小集是：
- `package.json` 增加 `@form-create/element-ui: ^3.2.37`
- `package.json` 增加 `fc-designer-pro: file:./fcDesignerPro/fc6.1`
- 只有在 `fcDesignerPro/fc6.1/dist` 实际存在时，才能在 `src/main.ts` 中 `import FcDesigner from 'fc-designer-pro/pc'` 并执行 `app.use(FcDesigner)` / `app.use(FcDesigner.formCreate)`
- 当前仓库里的 `fcDesignerPro/fc6.1` 只有包描述文件，没有 `dist`，所以现阶段不能注册 `fc-designer-pro/pc`，否则会报 `Can't resolve 'fc-designer-pro/pc'`

### 8.2 先区分“运行时表单”与“旧 iframe 表单”

- 新版 form-create 运行时/设计器接入完成后，只是具备了在当前 Vue3 前端直接渲染 `<form-create>` / `fc-designer` 的能力。
- 这不等于原来所有 Vue2 `fromcreat` iframe 页面都会自动迁移成功。
- 现有业务如果还是走 `dialog.vue#getLink('/formdesign/display/...')` 这种后端页面链路，就仍然要按旧 iframe 打开方式处理，不能混为一谈。

### 8.3 当前本地环境里“旧版可用 / 新版不可用”的准确含义

- 旧版现在能用的，是后端那套 `/formdesign/display/...`、`/formdesign/manager?...` 页面链路，所以旧版“设计器/编译器”在现有系统里仍然可访问。
- 新版现在已经验证“运行时”可用：`from_exampel_progect` 的 `/formcreate/example` 页面能正常渲染 `<form-create>`，说明 `@form-create/element-ui` 这条线没问题。
- 但新版“设计器/编译器”当前不能直接用，不是因为新版 form-create 理论上不支持，而是因为本地 `fcDesignerPro/fc6.1` 缺少真正的 `dist` 构建产物，无法注册 `fc-designer-pro/pc`。
- 所以当前项目里的准确结论应写成：
- 旧版：后端设计器/iframe 页面可用。
- 新版：运行时表单可用；设计器是否可用取决于后续是否补齐 `fcDesignerPro` 的 `dist`。

### 8.4 当前项目里验证新版运行时的最小入口

- 当前项目已增加隐藏测试页 `/formcreate/runtime-test`，对应文件是 `src/views/formcreate/RuntimeTest.vue`。
- 这个页面只验证一件事：当前项目能否直接使用新版 `<form-create>` + 手写 `rule` 渲染、交互、提交。
- 如果该页面可正常打开并提交提示成功，说明“当前项目可用新版运行时 form-create”这个结论已经在本项目内得到验证，不再只依赖 `from_exampel_progect` 作为旁证。

## 2026-03-09 15:15:48
- 冠心病筛查表单 253111114000173 的顶层 on.submit 原本只写入 window.tmp_sub_formData，standalone 通过 /formdesign/display/... 打开时不会自行发保存请求。
- 需要在该表单规则内补齐 `axios.post(contextPath + '/api/vab/mbscGxb/doEdit', formData)`，并兜底补 `RID/DAID/HZID/DEL_FLAG` 与成功后的 `closeLayers/doQuery/postMessage`。



## 2026-03-09 15:23:27
- 直接脚本替换 form-create 的 
ule.json 时，不能把 PowerShell 字面量 ` 
 ` 写进 JSON；否则会在 on.submit 与下一个属性之间产生非法字符，导致 Expected double-quoted property name。


## 2026-03-09 16:03:34
- MbscGxb_Mapper.xml 对 Oracle DATE 列不能直接依赖字符串隐式转换；SC_DATE/NEXT_SF_DATE/ZHUANZHEN_DATE/CREATE_TIME/UPDATE_TIME 需要显式 	o_date(nullif(...), 'yyyy-mm-dd hh24:mi:ss')。
- NEXT_SF_DATE、ZHUANZHEN_DATE 这类可空日期在 update 时要加 != '' 判断，否则前端传空字符串时也会触发 ORA-01861。
- 冠心病筛查 mapper 的 count/queryPage 条件字段应为 SHFEN_ID，不是 SHFE_ID。


## 2026-03-09 16:35:29
- fromCreate 表单若通过 URL 参数预填患者信息，页面入口与表单 onLoad 必须同时改：入口负责传 NAME/SHFEN_ID/SEX/AGE/HUANZHE_PHONE/MENZHEN_ID，表单 options.json 的新建分支负责 pi.setValue(...)。
- 仅在 Vue 页面 getLink(...) 传参而不修改 onLoad 时，253111114000173 这类表单不会自动把 URL 参数灌入字段。


## 2026-03-09 16:53:08
- 当前患者详情接口 getinfo() 返回的身份证字段在该环境下使用键名 shenfenZh；治疗方案/筛查表单预填 SHFEN_ID 时需要显式兼容这个键。


## 2026-03-09 17:18:23
- 冠心病治疗方案/筛查列表若要与其他慢病模块统一按档案 RID 查询，MBSC_JBXX 必须落库关联字段 MB_JBXX_FID；保存时由前端 DAID/jbxxidid 传入，后端写入 MBSC_JBXX.MB_JBXX_FID。
- MbscGxb_Mapper.xml 的 count/queryPage 改按 MB_JBXX_FID 查询后，前端列表可继续直接传当前页面 jbxxidid，不必再用身份证兜底。


## 2026-03-09 17:50:56
- Oracle + MyBatis 新增 varchar2 字段后，若插入/更新时报 Invalid column type，优先在 mapper 参数上显式指定 jdbcType=VARCHAR，例如 #{MB_JBXX_FID,jdbcType=VARCHAR}。

## 2026-03-09 18:12:44
- 旧后端/MyBatis 列表接口常直接返回数据库字段名（如 `SC_DATE`、`SHFEN_ID`、`HOS_NAME`），Vue3 页面若表格列使用驼峰字段（如 `scDate`、`shfenId`、`hosName`），需要先在列表层做 normalize，否则会出现“分页条数正确但单元格内容为空”。

## 2026-03-09 18:19:31
- 部分旧 MyBatis 列表 SQL 即使写了别名，实际返回给前端的键名仍可能被框架转成纯大写无下划线（如 `SCDATE`、`SHFENID`、`HOSNAME`、`TBRNAME`）；前端做 normalize 时要同时兼容驼峰、大写下划线、纯大写三种形态。


## 2026-03-09 18:40:26
- Oracle date 字段在 update 语句中不要直接绑定日期字符串；若 insert 已使用 to_date(...)，对应 update 也要保持同样格式转换，否则编辑保存时容易触发 ORA-01861。

## 2026-03-10 00:12:18

### 冠心病迁移总结（可复用到其余疾病）

#### 一、页面与表单入口

- 冠心病随访页入口：`src/views/hzinfo/gxbsffw/index.vue`
- 冠心病治疗方案页入口：`src/views/hzinfo/gxbzlfa/index.vue`
- 冠心病分级页入口：`src/views/hzinfo/gxbfenji/index.vue`
- 冠心病随访表单 RID：`253141810000185`
- 冠心病治疗方案/筛查表单 RID：`253111114000173`
- 冠心病治疗方案这块虽然业务上叫“治疗方案”，但当前实际使用的 fromCreate 表单元数据标题是“冠心病筛查”；RID 没错，按实际业务使用即可。

#### 二、随访与治疗方案的打开方式

- 随访页、治疗方案页最终都走旧 iframe 链路，不走本地 `<form-create>` 运行页。
- 正确组件是：`src/views/system2/linkIframe/dialog.vue`
- 正确打开方式是：`dlg.getLink('/formdesign/display/<RID>?callparent=on', params)`
- 不能用 `open_iframe_by_dialog.vue + 相对路径 /formdesign/display/...`，否则 iframe 会落到前端 `localhost:7788`，出现 `Cannot GET /formdesign/display/...`
- `dialog.vue` 在 Vue3 下要用 `el-dialog v-model="dialogFormVisible"`，不能继续用旧的 `visible.sync` / 错误的 `v-model:visible`
- `dialog.vue` 的 `getLink()` 会自动补 `baseURL`、token、随机串、`dialog=on`；旧表单依赖这套链路，不能随意改成手拼相对地址

#### 三、冠心病治疗方案列表的查询模型

- 其他疾病大多按“档案 RID / 患者基本信息 RID”查，不按身份证查。
- 冠心病治疗方案最开始后端写成了按 `MBSC_JBXX.SHFEN_ID` 查，导致前端传 `jbxxidid`（档案 RID）时列表查不到。
- 为了和其他疾病统一，最终改为按 `MBSC_JBXX.MB_JBXX_FID` 查。
- 前端列表请求保留：`gxbService.getList({ daid_eq: this.jbxxidid }, pageRequest)`
- 后端 `MbscGxbVabApiCtrl.java` 的 `getList()` 仍取 `daid_eq/daid`，但 mapper 现在用它去匹配 `MB_JBXX_FID`

#### 四、数据库结构补充（关键）

- `MBSC_JBXX` 必须新增字段：
- `MB_JBXX_FID varchar2(36)`：患者基本信息 RID / 档案 RID
- 建议索引：
- `create index IDX_MBSC_JBXX_MBJBXXFID on MBSC_JBXX (MB_JBXX_FID);`
- 原因：如果没有这个字段，治疗方案/筛查记录只能靠 `SHFEN_ID`（身份证）关联患者，无法和其他疾病一样按档案页 RID 统一查询。
- 旧数据若保存时还没有 `MB_JBXX_FID`，列表不会出来；需要：
- 重新编辑保存一遍，让后端补写 `MB_JBXX_FID`
- 或用 SQL 按 `RID` / `SHFEN_ID` 回填历史数据

#### 五、后端改动点（治疗方案/筛查）

- Controller：`D:\ProPro\JAVA\Olde\ygt-mb\ygt_manbing_java_ef4\manbing-core\src\main\java\com\ccesun\mb\web\ctrl\mbsc\MbscGxbVabApiCtrl.java`
- Mapper：`D:\ProPro\JAVA\Olde\ygt-mb\ygt_manbing_java_ef4\manbing-core\src\main\resources\mybatis\autoscan\mbsc\MbscGxb_Mapper.xml`
- 关键结论：
- `doEdit()` 保存时必须把前端传来的 `MB_JBXX_FID` / `DAID` 写入 `mainData.put("MB_JBXX_FID", ...)`
- `count/queryPage` 必须按 `MB_JBXX_FID = #{daid}` 查
- 新增 `varchar2` 字段（如 `MB_JBXX_FID`）时，MyBatis 参数最好显式写：`#{MB_JBXX_FID,jdbcType=VARCHAR}`，否则容易报 `Invalid column type`
- Oracle `date` 字段不能直接拿字符串 update；`insert` 和 `update` 都要统一写成 `to_date(nullif(...), 'yyyy-mm-dd hh24:mi:ss')`

#### 六、当前前端患者预填规则（治疗方案新建）

- 页面：`src/views/hzinfo/gxbzlfa/index.vue`
- 当前新建时会通过 URL 传：
- `NAME`
- `SHFEN_ID`
- `SEX`
- `AGE`
- `HUANZHE_PHONE`
- `MENZHEN_ID`
- `DAID / HZID / MB_JBXX_FID`
- 当前患者详情接口 `getinfo()` 在该环境里身份证字段实际键名是 `shenfenZh`，治疗方案预填必须显式兼容这个键
- 其余兼容键还包括：`shenfenzhm / shfenzhm / sfzh / idCard / shenfenzhengHao`

#### 七、fromCreate 事件沉淀（重点，后续疾病可直接套）

##### 1）最终生效的事件

- `onCreated(api)`：当前验证是生效的
- `onSubmit(formData, api)`：当前验证是生效的
- `onLoad(api)`：这次在 display 运行时不稳定，编辑回填没有依赖它，最终把核心逻辑移到 `onCreated`
- `beforeSubmit / beforeFetch`：可保留，但这次真正起作用的是 `onSubmit`

##### 2）`onCreated(api)` 的职责

- 编辑模式：如果 URL 上有 `rid`
- 调 `/api/vab/mbscGxb/get`
- 拿到单条详情后 `api.setValue(data)`
- 在 `api.setValue` 之前，把多选字段从字符串转数组
- 新建模式：如果没有 `rid` 但有 `daid`
- 从 URL 取患者信息
- `api.setValue(...)` 预填患者姓名、身份证、性别、年龄、联系电话、门诊 ID
- 同时补：
- `DAID`
- `HZID`
- `MB_JBXX_FID`
- `DEL_FLAG`

##### 3）编辑回填时必须转回数组的字段

- `JW_SHI`
- `JIAZUSH`
- `SPORT`
- `GXB_WEIXIAN`
- `ZHENDUAN_JIANCHA`
- `SF_FANGAN`

- 这些字段如果后端返回的是 `"1,2,3"`，编辑回填时必须 `split(',') -> array`，否则：
- 多选回填状态不对
- 选项无法正常二次编辑

##### 4）`onSubmit(formData, api)` 的职责

- 提交前统一补齐：
- `RID`
- `DAID`
- `MB_JBXX_FID`
- `HZID`
- `DEL_FLAG`
- 统一处理日期：
- `SC_DATE`
- `NEXT_SF_DATE`
- `ZHUANZHEN_DATE`
- 如果是 `yyyy-MM-dd`，补成 `yyyy-MM-dd 00:00:00`
- 统一把数组字段转成逗号分隔字符串再提交

##### 5）提交前必须 `join(',')` 的字段

- `TABLE_NAMES`
- `DUOXUAN`
- `JIAZUSH`
- `JW_SHI`
- `SPORT`
- `GXB_WEIXIAN`
- `ZHENDUAN_JIANCHA`
- `SF_FANGAN`

- 只要 payload 里这些字段还是数组，后端绑定到 Oracle `varchar2` 时就很容易报：
- `Invalid column type`

#### 八、列表页字段显示的经验

- 冠心病治疗方案列表请求虽然 SQL 里写了驼峰别名，但前端实际拿到的字段名可能有三种形态：
- 驼峰：`scDate`
- 大写下划线：`SC_DATE`
- 纯大写无下划线：`SCDATE`
- 当前 `src/views/hzinfo/gxbzlfa/index.vue` 已通过 `normalizeRow()` 同时兼容：
- `RID`
- `SCDATE / SC_DATE / scDate`
- `SHFENID / SHFEN_ID / shfenId`
- `HOSNAME / HOS_NAME / hosName`
- `TBRNAME / TBR_NAME / tbrName`
- `TBRPHONE / TBR_PHONE / tbrPhone`
- `UPDATETIME / UPDATE_TIME / updateTime`
- 如果看到“分页显示有条数、操作列有按钮，但内容列空白”，优先怀疑是字段名 normalize 没做全，不是接口没数据

#### 九、这次冠心病实际踩过的坑（后续疾病直接避开）

- 1. 用错 iframe 组件
- `open_iframe_by_dialog.vue` 不适合这套旧 formdesign 页面链路；旧表单应统一走 `dialog.vue#getLink()`

- 2. 直接用相对路径打开 `/formdesign/display/...`
- 会打到前端 dev server，出现 `Cannot GET /formdesign/display/...`

- 3. Element Plus 日期格式 token 不同
- Vue2/ElementUI 里的 `yyyy-MM-dd` 迁到 Vue3/Element Plus 后，必须改成 `YYYY-MM-DD`
- 否则会出现类似 `yyyy-02-日`

- 4. 旧 mapper 字段名手误
- `SHFE_ID` 实际应为 `SHFEN_ID`

- 5. Oracle `date` 列不能依赖字符串隐式转换
- `SC_DATE / NEXT_SF_DATE / ZHUANZHEN_DATE / CREATE_TIME / UPDATE_TIME` 都要显式 `to_date(...)`

- 6. 新增 Oracle `varchar2` 字段时直接绑定可能报 `Invalid column type`
- 例如 `MB_JBXX_FID` 最终需要写成 `#{MB_JBXX_FID,jdbcType=VARCHAR}`

- 7. display 模式下 `onLoad` 不一定是最稳入口
- 这次编辑回填最终依赖 `onCreated(api)`，不要默认 `onLoad` 一定可靠

- 8. 编辑回填时多选字段如果不转数组
- 会出现“勾选状态不对/无法重新选择”

- 9. 编辑保存时多选字段如果不转字符串
- 会出现 `Invalid column type`

- 10. 列表字段名不是固定一种风格
- 旧系统返回值可能是驼峰/大写下划线/纯大写三种之一，列表层必须 normalize

- 11. 历史数据不会自动补新加关联字段
- 新增 `MB_JBXX_FID` 后，旧数据需要回填或重新保存，否则列表按档案 RID 查不到

#### 十、其余疾病迁移时的建议顺序

- 1. 先确定该疾病“随访 / 治疗方案 / 分级”各自对应的表单 RID 或页面入口
- 2. 优先判断该表单走：
- 旧 iframe `/formdesign/display/...`
- 还是本地 `<form-create>` 运行页
- 3. 如果是旧 iframe：
- 直接复用 `dialog.vue#getLink()`
- 不要自行换成别的弹窗链路
- 4. 先打通新建
- 能打开
- 能保存
- 能刷新列表
- 5. 再做编辑
- 先确认单条详情接口
- 再确认 fromCreate `onCreated` 回填逻辑
- 6. 最后统一列表
- 查询条件尽量和其他疾病一样按档案 RID
- 如果数据库缺关联字段，先补字段再改查询

#### 十一、可直接复用到其他疾病的检查清单

- 是否有对应表单 RID
- 是否需要 `MB_JBXX_FID` 这类档案关联字段
- 列表接口到底按什么查：身份证 / 档案 RID / 主表 RID
- 详情接口是否可用于编辑回填
- fromCreate 编辑回填时哪些字段是多选，需要 `split(',')`
- fromCreate 提交时哪些字段是数组，需要 `join(',')`
- 日期字段在 Oracle mapper 里是否统一 `to_date(...)`
- 列表返回字段名是否需要 `normalizeRow()`

## 2026-03-10 10:30:00

- 脑卒中当前在本项目内已确认可直接复用现成接口链路 `src/api/naozuzhong/MbNzzFwb1mainZs_api.ts`，对应后端是 `/api/vab/mbNzzFwb1mainZs/*`，这是“脑卒中服务管理”接口，不是冠心病那套 iframe/form-create 筛查链路。
- 脑卒中这轮先打通的是患者详情页入口 `/hzinfo/nzzsffw/:jbxxidid` + 列表页；查询条件当前按 `danganid_eq = jbxxidid` 传给 `mbNzzFwb1mainZs/getList`。
- `mbNzzFwb1mainZs` 列表返回字段名同样不能假定只有一种风格，页面层需要兼容驼峰、大写下划线和纯大写三种键名形态后再渲染。

## 2026-03-10 11:00:00

- 脑卒中 `mbNzzFwb1mainZs` 这套接口虽然现成可用，但它对应的是“脑卒中服务管理”，不是患者详情页里要对齐冠心病的“人员分级级别 / 治疗方案 / 随访”三块业务。
- 如果用户要求“脑卒中部分跟冠心病一样”，前端患者页结构应优先对齐冠心病：左侧菜单拆成 `nzzfenji / nzzzlfa / nzzsffw` 三项，再逐块承接真实后端；不要直接把 `mbNzzFwb1mainZs` 挂成唯一入口。
- 当后端尚未补齐时，先把患者页路由、菜单和页面骨架建好，并在页面内明确标注“后端链路待补”，这样比接错旧接口更安全，也更便于后续逐块替换为真实实现。

## 2026-03-10 11:35:00

- 脑卒中表单设计目录里已确认两个可直接复用的 RID：
- `253111404000176`：标题为“脑卒中筛查”，表名包含 `MBSC_JBXX, MBSC_NZZ`，前端可先挂到“治疗方案/筛查”入口，走旧 `/formdesign/display/...` iframe 链路。
- `253161516000188`：标题为“脑卒中随访”，表名包含 `MBSF_YAOWU, NAOZUZHONG_SFFW`，前端可先挂到“随访”入口，走旧 `/formdesign/display/...` iframe 链路。
- 当前 Vue3 项目内未找到与脑卒中治疗方案/随访直接对应的独立列表 API；如果只确认到 RID，没有确认到列表接口，就先做“可新建表单 + 页面提示待补”，不要为了凑齐管理页去复用 `mbNzzFwb1mainZs`。
- 脑卒中“人员分级级别”截至目前仍未定位到旧页面或后端接口，需继续从旧项目、设计目录或 Java controller 命名中单独排查。

## 2026-03-10 14:05:00

- `Learing/MB_NZZ_1ZHKZMB_ZS.txt` 与 `Learing/MB_NZZ_ZHKZMB_ITEM_ZS.txt` 已确认落库；脑卒中分级前端应以这两张表的字段命名为准，不再假设沿用冠心病主表字段名。
- 脑卒中主表里风险分层字段已经不是冠心病的 `xxgwxysfc_result/qt`，而是 `nzzwxysfc_result/qt`；前端表单、列表、API DTO 命名都要同步切到 `nzzwxysfcResult / nzzwxysfcQt`。
- 脑卒中综合控制目标明细表 `MB_NZZ_ZHKZMB_ITEM_ZS` 结构与冠心病 `MB_GXB_ZHKZMB_ITEM_ZS` 一致，可直接复用“batch_fid + field_code + val_text”这套保存逻辑；差异主要在表名、字典类型 `MB_NZZ_ZHKZMB` 和指标编码。
- 这轮前端将脑卒中综合控制目标页面收口为“血压 / 血糖 / 血脂”三项，使用 item 表的动态编码保存；如果后端字典实际拆成 `XYG/XYD/XT/XZ` 等更细项，需要再和页面编码保持一致。

## 2026-03-10 14:35:00

- 脑卒中分级后端可以直接照冠心病 `MbGxb1zhkzmbZs` 复制一套，但要把主表实体字段从 `xxgwxysfcResult/Qt` 改成 `nzzwxysfcResult/Qt`，否则和 `MB_NZZ_1ZHKZMB_ZS` 对不上。
- `FastMbNzz1zhkzmbZsServiceImpl#doSaveIt` 的核心逻辑仍然是：
  - 主表新增/更新
  - 清空旧 `MB_NZZ_ZHKZMB_ITEM_ZS`
  - 把 `streamsAsJson.dynfield` 全量写入 item 表
- 由于 item 表保存的是 `dynfield` 全量键值，前端 `dynfield` 里只要传了 `guanliFenlei / fwbfid / XY / XT / XZ / 家族史相关字段`，后端无需再逐字段硬编码映射。
- 当前补的 Java 后端链路只覆盖了前端这轮实际使用到的主表公共字段与风险分层字段；如果后续前端要直接读写 `JBFL / HBJB_* / BFZ_* / ZHKZMB_*` 这些主表列，再继续把实体字段补完整即可。

## 2026-03-10 15:05:00

- 脑卒中分级页“基本分类”字典名已确认使用 `NZZ_ICD`，不要再接 `MB_NZZ_JBFL`；如果页面出现“基本分类下拉无数据”，优先检查这个字典类型是否写错。
- `src/utils/tools.ts#doCommonHandle` 不能假设错误对象最终一定是字符串；接口 404/500 或 axios 异常对象进入通用提示时，需要先归一化成字符串再做 `split('<br/>')`，否则会出现二次报错 `errMsg.split is not a function`，把原始后端错误覆盖掉。

## 2026-03-10 16:05:00

- 脑卒中治疗方案表单 `253111404000176` 虽然标题是“脑卒中筛查”，但在患者详情页业务上可直接承接“治疗方案”入口；真实链路是 `MBSC_JBXX + MBSC_NZZ`，不能继续停留在示例提交地址 `/theexampleapi/submitFormDataInfo`。
- 这张表单的真实后端应补为 `/api/vab/mbscNzz/getList|get|doEdit|doDelete`，实现方式可直接复制冠心病 `MbscGxb`，主表仍是 `MBSC_JBXX`，从表改为 `MBSC_NZZ`。
- `MBSC_NZZ` 相对冠心病当前确认的病种特有字段主要是：`NZZ_WEIXIAN`、`PG_NZZ_QUEXUE`、`PG_NZZ_CHUXUE`；其余治疗、随访方案、转诊字段大体与冠心病筛查表一致。
- 表单 `253111404000176.options.json` 需要像冠心病一样显式补 `onCreated/onSubmit`：
  - 新建模式从 URL 预填 `NAME/SHFEN_ID/SEX/AGE/HUANZHE_PHONE/MENZHEN_ID`
  - 编辑模式调用 `/api/vab/mbscNzz/get`
  - 多选字段至少要处理 `JW_SHI/JIAZUSH/SPORT/NZZ_WEIXIAN/ZHENDUAN_JIANCHA/SF_FANGAN`
  - 日期字段继续统一补到 `yyyy-MM-dd 00:00:00`

## 2026-03-10 16:20:00

- 脑卒中治疗方案列表后端不能只按 `MBSC_JBXX.MB_JBXX_FID` 查主表，否则同一患者名下其他病种也写在 `MBSC_JBXX` 的筛查记录会被一起带出来。
- `MbscNzz_Mapper.xml` 的 `count/queryPage` 必须显式 `inner join MBSC_NZZ on MBSC_NZZ.RID = MBSC_JBXX.RID`，用从表存在性来限定“这条记录确实属于脑卒中”；前端即使已经走 `mbscNzz` API，也挡不住后端少这层约束。

## 2026-03-10 16:35:00

- 脑卒中筛查/治疗方案表单 `253111404000176` 当前确认的业务多选字段至少包括：
  - `JW_SHI`
  - `JIAZUSH`
  - `SPORT`
  - `HEIGHT_PG`
  - `NZZ_WEIXIAN`
  - `ZHENDUAN_JIANCHA`
  - `PG_NZZ_QUEXUE`
  - `PG_NZZ_CHUXUE`
  - `SF_FANGAN`
- 这些字段在保存前必须统一 `join(',')`，编辑回填时必须统一 `split(',')`；否则 Oracle/MyBatis 往 `varchar2` 列写数组时会报 `Invalid column type`。

## 2026-03-10 17:05:00

- 脑卒中随访 `253161516000188` 不是一套待新建的专属后端，它直接复用老慢病随访公共接口：
  - 列表：`/tjxt/sffw/querySffwPage`
  - 明细：`/tjxt/sffw/fetchDetail`
  - 保存：`/tjxt/sffw/saveSffwInfo`
  - 删除/状态更新：`/tjxt/sffw/changeState`
- 这套随访公共接口查询主键不是档案 `DAID`，而是 `HUANZHE_FID`；设计稿 `12查询列表.ms` 里显式参数也是 `page / limit / HUANZHE_FID`。如果患者页随访列表查不到数据，优先检查是否还在传 `daid_eq`。
- 脑卒中随访患者页展示字典要使用脑卒中专属类型：
  - `mbSF_naoZZ_suiFangFangShi`
  - `mbSF_naoZZ_zhengZhuang`
  不能继续沿用冠心病的 `mbSF_guanXB_*`。
- 删除随访记录不是物理删除，而是调用 `/tjxt/sffw/changeState` 把 `DEL_FLAG` 更新为 `1`；前端列表默认只查 `DEL_FLAG=0`。

## 2026-03-10 17:15:00

- 患者详情页左侧服务菜单要保持各病种同层级结构一致：如果冠心病/高血压/糖尿病的“随访及检查检验”是 `el-sub-menu`，脑卒中也必须做成 `el-sub-menu`，不能偷简化成普通 `el-menu-item`，否则视觉层级、展开箭头、激活态和子项缩进都会不一致。

## 2026-03-10 17:35:00

- 脑卒中随访患者页如果直接复用老 `tjxt/sffw` 接口，不要默认走项目统一 `request` 封装；这套老接口返回格式和当前前端成功码约定不完全一致，更稳的做法是页面层直接取原始 `axios` 响应再自己做分页解析。
- 脑卒中随访表单 `253161516000188` 的 `onCreated` 至少要先执行一次 `api.setValue('HUANZHE_FID', HUANZHE_FID)`，再异步查患者详情；否则患者详情接口慢或查不到时，保存会直接报“参数[HUANZHE_FID]为必填项”。
- 脑卒中随访保存脚本也要像冠心病一样在提交前兜底：
  - 先取 `c_Formdata.HUANZHE_FID`
  - 再取 `var_huanzheinfo.RID / HUANZHE_FID`
  - 最后回退到 URL 参数 `hzid/HZID`
- 脑卒中随访表单当前确认的多选字段至少包括 `ZHENGZHUANG`；如果后面启用了饮酒类别等多选项，提交前也要同步 `join(',')`。

## 2026-03-10 17:45:00

- 当前 Vue3 的 `src/views/system2/linkIframe/dialog.vue` 关闭逻辑依赖 `window.postMessage`，不是旧项目里表单脚本常写的 `window.parent.closeLayers()`。
- 表单设计稿如果运行在患者页 `IframeDialog` 里，保存成功后的父页面通知应优先：
  - `window.parent.postMessage({ type: 'form-create-success', data: ... }, '*')`
  - 旧的 `closeLayers()` / `doQuery()` 只能作为“函数存在时的兼容调用”，不能直接假设父页面一定有这两个方法。

## 2026-03-10 18:10:00

- 主菜单动态路由如果后端仍下发旧 Vue2 组件路径（例如 `@views/gaoxueya/gxyJbxx/Ys_MbJbxx02infoZsIndex`），而 Vue3 仓库中没有同路径文件，就会被 `src/utils/routes.ts` 自动兜底到 `src/views/_migration/MissingView.vue`，页面表现为“页面尚未迁移”。
- 这类主名单页迁移，最稳妥的第一步不是改后端路由，而是在 Vue3 中先补齐同路径组件文件，优先复用同类病种已迁好的名单页结构，再替换成对应病种的 API、字典和患者详情入口。
- 高血压相关筛选字典（如 `MB_GXY_ZHONGLEI`、`MB_GXY_FENLEI`、`MB_GXY_CQSF_FJGL`）不要默认从 `src/utils/kit/inner_dict.ts` 静态导入；该文件未必导出了这些枚举。更稳的做法是像高血压分级表单一样，页面初始化时用 `dictArray(...)` 动态加载。

## 2026-03-11 00:10:00

- 患者页左侧菜单里的“按年随访分类”不是前端自己算的，完全依赖后端 `MbTnbSffw1mainZs_Mapper.xml -> make_menu_dir` 返回的 `kind / the_year / jlsize`。
- 如果出现“糖尿病有年份分类，但冠心病/脑卒中只有全部随访”，优先检查 `make_menu_dir` 是否只 union 了 `10tnbsffw / gxysffw`，漏掉了：
  - `gxbsffw` -> `mb_gxb_sffw_1main_zs`
  - `nzzsffw` -> `mb_tjxt_sffw`
- 当前这套项目里脑卒中随访年份统计要按 `MB_TJXT_SFFW.HUANZHE_FID=#{jbxxid}` 查，并过滤 `DEL_FLAG='0'`；不要误按 `MB_JBXX_FID` 去查脑卒中公共随访表。

## 2026-03-11 00:20:00

- 左侧菜单 `make_menu_dir` 是全病种共享 SQL，只要其中任意一个 `union all` 引用了当前库不存在的表/视图，整个患者页左侧菜单都会报错，连糖尿病原本正常的年份分类也会一起消失。
- 所以给共享菜单补病种年份统计时，必须先确认目标库里真实存在那张表；如果还没确认，宁可先不接该病种年份，也不能把不确定的表名直接写进 `make_menu_dir`。
- 当前已确认可安全接入：
  - `10tnbsffw` -> `mb_tnb_sffw_1main_zs`
  - `gxysffw` -> `mb_gxy_sffw_1main_zs`
  - `gxbsffw` -> `mb_gxb_sffw_1main_zs`
- 当前脑卒中 `nzzsffw` 的年份统计表名在这套库里还没最终确认，先不要再把 `MB_TJXT_SFFW` 写回共享菜单 SQL。 

## 2026-03-11 00:30:00

- 当前项目先恢复到“原始可用状态”：左侧菜单按年分类只保留糖尿病和高血压；冠心病、脑卒中暂时不接年份菜单。
- 在没有把冠心病、脑卒中的年份统计链路单独核实清楚前，不要继续改共享 `make_menu_dir`，否则很容易一处改动影响所有病种患者页。 

## 2026-03-11 11:10:00

- 患者页左侧菜单里的年份项即使模板写了 `make_link_url('gxysffw', 'year=' + item2.the_year)`，也要确认 `src/utils/manbingkit.ts -> leftmenu_make_link_url` 真的会把查询串拼进 URL；当前项目里如果保留 `urlkind == '111'` 这种硬编码判断，随访年份菜单会看起来存在、实际永远跳到“全部随访”。
- 如果左侧菜单 `el-menu` 开了 `:router="true"`，年份项的 `el-menu-item :index` 不能继续放不带查询串的纯路径；否则 Element 会按 `index` 导航并覆盖内部 `router-link` 的 `?year=xxxx`，结果就是地址栏没有年份参数、右侧列表也拿不到年份。
- 这种带查询串的患者页菜单高亮，`default-active` 也要优先使用 `$route.fullPath`，只用 `$route.path` 会把“全部随访”和“2026 年随访”误判成同一个激活项。
- 糖尿病/高血压患者随访页当前“按年筛选”更稳的前端接法是：
  - 读取 `$route.query.year`
  - 年份模式下把列表请求页大小临时提到较大值（当前先用 1000）
  - 再按 `suifangRq` 的 `yyyy-` 前缀做前端过滤
- 这样做的原因是现有 `getList` 虽然支持 `suifangRq` 查询，但前端当前并没有明确的 `ParamEntry` 组装工具可安全传 `BETWEEN`/`LIKE`；在未补清楚那套查询对象协议前，直接依赖路由年份 + 前端过滤更稳。 
- 冠心病患者页随访当前走独立接口 `src/api/guanxinbing/MbGxbSffw1mainZs_api.ts -> /api/vab/mbGxbSffw1mainZs/getList`，请求里实际筛选键是 `form: { daid_eq, data_state_eq }`；如果要做年份态，优先沿用患者页路由 `?year=xxxx` 并在页面层按 `suifangRq` 过滤，而不是直接猜后端复杂查询对象。
- 脑卒中患者页随访当前复用老公共接口 `/tjxt/sffw/querySffwPage`，关键主键是 `HUANZHE_FID`，不是 `MB_JBXX_FID/DAID`；它的年份菜单统计如果后端直查表，也必须按 `MB_TJXT_SFFW.HUANZHE_FID=#{jbxxid}` 并过滤 `DEL_FLAG='0'`。
- 当前共享左侧菜单 SQL `MbTnbSffw1mainZs_Mapper.xml -> make_menu_dir` 已补的年份来源关系为：
  - `10tnbsffw` -> `mb_tnb_sffw_1main_zs.MB_JBXX_FID`
  - `gxysffw` -> `mb_gxy_sffw_1main_zs.MB_JBXX_FID`
  - `gxbsffw` -> `mb_gxb_sffw_1main_zs.MB_JBXX_FID`
  - `nzzsffw` -> `mb_tjxt_sffw.HUANZHE_FID` 且 `DEL_FLAG='0'`

## 2026-03-11 12:00:00

- 慢阻肺这轮先不要误接现有 `MbMzfFwb1mainZs` 精细化管理/服务管理链路；如果业务目标是对齐冠心病/脑卒中的“人员分级级别 / 治疗方案 / 随访及检查检验”，第一步应先补患者页三块入口和左侧菜单结构。
- 在还没确认慢阻肺分级、治疗方案、随访各自真实后端或表单 RID 前，页面层先保留患者信息头 + 左侧菜单 + 占位提示最安全，这样不会把现有精细化管理误当成三块业务中的任意一块。
- 左侧菜单新增病种时，要同时补三处：
  - `src/router/index.ts` 路由入口
  - `src/utils/manbingkit.ts` 的 `leftmenu_urlmapping`
  - `src/views/hzinfo/jbxxeditor/components/LeftMenu.vue` 的菜单结构和默认空数组兜底

## 2026-03-11 12:20:00

- 如果某个新病种暂时还没有可用后端，不要一直停留在单页 `el-alert` 占位；更稳的中间态是先补成和已迁病种一致的“列表页 + 抽屉 + 表单”三件套外壳，这样后续补接口时只需要往固定承载点接数据。
- 当前慢阻肺已确认的 form-create RID 有：
  - `253111420000179`：慢阻肺筛查，表名 `MBSC_JBXX,MBSC_MZF`
  - `253171402000191`：慢阻肺随访，表名 `MBSF_YAOWU,MANZUFEI_SFFW`
- 这两个 RID 只能先作为治疗方案/随访的候选入口；它们不等于“人员分级级别”链路，分级页不要因为看到慢阻肺现成服务管理或筛查表单就直接误接过去。

## 2026-03-11 13:10:00

- 慢阻肺治疗方案链路可直接按脑卒中 `mbscNzz` 复制为 `mbscMzf`：主表仍是 `MBSC_JBXX`，从表改成 `MBSC_MZF`，患者页列表查询同样必须 `inner join MBSC_MZF`，否则同一档案下其他病种的筛查记录会混进来。
- 慢阻肺筛查表单 `253111420000179` 当前确认的病种特有多选字段至少包括：
  - `MZF_WEIXIAN`
  - `ZHENDUAN_JIANCHA`
  - `PG_MZF`
  - 再加上公共多选 `JW_SHI / JIAZUSH / SPORT / HEIGHT_PG / SF_FANGAN`
- 这些字段在提交到 `/api/vab/mbscMzf/doEdit` 前要统一 `join(',')`，编辑回填时要统一 `split(',')`，否则仍会踩到 Oracle `varchar2` 的 `Invalid column type`。
- 慢阻肺随访 `253171402000191` 已在设计稿里明确使用公共随访接口：
  - 字典 `mbSF_manZF_suiFangFangShi`
  - 字典 `mbSF_manZF_zhengZhuang`
  - 明细 `/tjxt/sffw/fetchDetail`
  - 保存 `/tjxt/sffw/saveSffwInfo`
- 这说明慢阻肺随访患者页更适合像脑卒中一样直接复用公共 `tjxt/sffw`，而不是误接 `MbMzfFwb1mainZs` 那套慢阻肺服务管理/精细化接口。
- 慢阻肺随访表单也要像脑卒中一样在 `onCreated` 里先执行一次 `api.setValue('HUANZHE_FID', hzid)`，保存成功后优先补 `window.parent.postMessage({ type: 'form-create-success' }, '*')`，这样 Vue3 `IframeDialog` 才能稳定关闭并刷新父页列表。

## 2026-03-11 13:25:00

- 患者页左侧菜单的年份分类 SQL `MbTnbSffw1mainZs_Mapper.xml -> make_menu_dir` 是全病种共享入口，只要其中任意一个 `union all` 命中当前库不存在的表，进入任意病种患者页都可能直接报 `ORA-00942`。
- 当前环境里 `mb_tjxt_sffw` 并不稳定可用，因此脑卒中 `nzzsffw` 的年份统计不能继续写在共享 `make_menu_dir` 里；否则像慢阻肺治疗方案这种看似无关的患者页也会被左侧菜单初始化一起带挂。
- 处理原则仍然是：共享菜单先保证“整页能进”，病种年份统计宁可暂时撤回，也不要把不确定表名继续放在共享 SQL 里。 

## 2026-03-11 13:35:00

- 如果共享菜单报错截图里的 SQL 已经不再包含某个已撤回病种，但仍然是 `MbTnbSffw1mainZs_Mapper.xml -> make_menu_dir` 报 `ORA-00942`，说明还要继续顺着 SQL 中剩余的 `union all` 检查当前库是否真有那张表。
- 这次继续排查后确认：当前库没有 `MB_GXB_SFFW_1MAIN_ZS`，所以冠心病 `gxbsffw` 的年份统计同样不能继续保留在共享 `make_menu_dir` 中；否则进入任意患者页仍会因为左侧菜单初始化失败而整页弹错。

## 2026-03-11 14:05:00

- 慢阻肺患者页随访 `src/views/hzinfo/mzfsffw/index.vue` 目前仍然只能走公共 `/tjxt/sffw/querySffwPage`，因为仓库里没有与冠心病 `mbGxbSffw1mainZs` 对应的 `mbMzfSffw1mainZs` 专病列表接口。
- 已确认 `MbMzfFwb1mainZs` 属于“慢阻肺服务管理/精细化管理主表”，不是患者页“随访及检查检验”这一块的直接替代物，不能为了隔离病种就直接把患者页列表切到 `MbMzfFwb1mainZs`。
- 这套公共随访表单 `253171402000191` 的专病差异当前主要体现在字典来源：
  - `mbSF_manZF_suiFangFangShi`
  - `mbSF_manZF_zhengZhuang`
- 所以慢阻肺患者页列表的稳妥做法是：
  - 先按 `HUANZHE_FID + DEL_FLAG=0` 查询公共随访
  - 再在前端只保留“命中慢阻肺字典值”或“带慢阻肺显式标记字段”的记录
- 这意味着当前慢阻肺随访页的分页总数不能直接相信后端原始 `total`，页面展示的总数应以过滤后的慢阻肺记录数为准；否则会出现表格只剩 1 条、分页却仍显示 2 条以上的错位。

## 2026-03-11 14:20:00

- 对照脑卒中患者页后确认：`src/views/hzinfo/nzzsffw/index.vue` 并没有做任何“专病过滤”，而是直接展示公共 `/tjxt/sffw/querySffwPage` 返回的患者随访记录，所以新建成功后能立即在列表看到。
- 慢阻肺页如果在前端额外加“字典命中/显式标记”过滤，会出现一个直接副作用：`/tjxt/sffw/saveSffwInfo` 虽然提示保存成功，但因为公共返回结果里没有稳定的慢阻肺专病标识，新记录可能立刻被页面过滤掉，看起来像“没保存”。
- 当前阶段更稳的处理是先与脑卒中保持一致，撤回前端专病过滤，优先保证保存后的可见性；病种隔离问题需要等后端能明确提供：
  - 慢阻肺专病标识字段
  - 或独立慢阻肺随访列表接口

## 2026-03-11 15:00:00

- 慢阻肺这块最终不能继续参照脑卒中的公共 `/tjxt/sffw`，而要参照冠心病的专病随访模式来做。
- 当前库里已确认的慢阻肺专病随访主表就是 `MANZUFEI_SFFW`；药物明细仍然复用公共 `MBSF_YAOWU`，这和冠心病 `GUANXINBING_SFFW + MBSF_YAOWU` 的结构一致。
- 因此慢阻肺患者页“随访及检查检验”的正确链路应为：
  - 列表：`/api/vab/mbMzfSffw1mainZs/getList`
  - 明细：`/api/vab/mbMzfSffw1mainZs/get`
  - 保存：`/api/vab/mbMzfSffw1mainZs/doEdit`
  - 删除：`/api/vab/mbMzfSffw1mainZs/doDelete`
- 表单 `253171402000191` 虽然设计期一度写成公共 `fetchDetail/saveSffwInfo`，但只要数据库里已经有明确专病主表 `MANZUFEI_SFFW`，就应该切成专病 API；否则会出现“保存成功但患者页专病列表查不到”或者“和其他病种公共随访混在一起”的问题。
- 这套专病接口实现时可以直接复制冠心病 `MbGxbSffw1mainZs` 的 controller/mapper 结构，只需要替换：
  - 主表名：`GUANXINBING_SFFW` -> `MANZUFEI_SFFW`
  - namespace / controller 名称
  - `MBSF_YAOWU.ZHUBIAO_TYPE`：`GUANXINBING_SFFW` -> `MANZUFEI_SFFW`
- 但不能机械地把冠心病 mapper 全量照搬到慢阻肺。当前已确认 `MANZUFEI_SFFW` 至少不兼容冠心病里的 `QT_RIYIN_MB` 这一类饮酒细分列；出现 `ORA-00904` 时，要以慢阻肺表单当前真实字段和表结构为准，把 mapper 收缩到实际存在的列。
- 当前慢阻肺专病链路的查询/删除也应优先按公共随访这套真实主键语义处理：
  - 患者主键：`HUANZHE_FID`
  - 删除标记：`DEL_FLAG`
  - 不要继续沿用冠心病的 `DAID + DATA_STATE` 作为唯一过滤条件

## 2026-03-11 16:30:00

- `Learing/MANZUFEI_SFFW.txt` 已确认慢阻肺专病随访主表真实结构，和冠心病 `GUANXINBING_SFFW` 并不完全相同。
- 最关键的两点更正：
  - 患者关联键应使用 `DAID`
  - 有效数据状态应使用 `DATA_STATE='1'`
- 这意味着慢阻肺专病随访不能继续沿用前一版临时实现里的 `HUANZHE_FID + DEL_FLAG`；否则会出现“保存成功但列表查不到”或软删语义不一致。
- `MANZUFEI_SFFW` 中不存在 `SFDB`，但存在以下慢阻肺表特有或当前必须保留的列：
  - `TIZHONG2`
  - `BMI2`
  - `KOUCHUN_ZG`
  - `XIAZHI_SZ`
  - `QITA_TZ`
  - `HXDL_CI_ZHOU`
  - `HXDL_FEN_ZHONG_CI`
  - `JIANKANG_JY`
  - `SHEYAN_QK`
  - `DAID`
  - `DATA_STATE`
- 因此 `MbMzfSffw1mainZs_Mapper.xml` 的 insert/update/count/query/delete 都必须以 `MANZUFEI_SFFW.txt` 为准，而不是简单从冠心病 mapper 裁剪。
- 表单 `253171402000191` 也必须同步这套真实语义：
  - 新建默认值写 `DAID`
  - 默认状态写 `DATA_STATE='1'`
  - 保存前删除临时兼容字段 `HUANZHE_FID`、`DEL_FLAG`
  - 编辑回填时优先用 `row.DAID` 回查患者信息

## 2026-03-11 17:05:00

- `Learing/NAOZUZHONG_SFFW.txt` 已确认脑卒中专病随访主表真实存在，且结构与 `GUANXINBING_SFFW` 基本同型，患者主键语义同样是：
  - `DAID`
  - `DATA_STATE`
- 当前仓库原始脑卒中患者页和表单并没有使用 `NAOZUZHONG_SFFW`，而是走公共随访链路：
  - 页面列表：`/tjxt/sffw/querySffwPage`
  - 明细：`/tjxt/sffw/fetchDetail`
  - 保存：`/tjxt/sffw/saveSffwInfo`
  - 删除：`/tjxt/sffw/changeState`
  - 这些公共接口实际落库表是 `MB_TJXT_SFFW`，不是 `NAOZUZHONG_SFFW`
- 既然 `253161516000188.txt` 的 `TABLE_NAMES` 已明确写成 `MBSF_YAOWU,NAOZUZHONG_SFFW`，脑卒中患者页“随访及检查检验”就应该按专病表修正回来，而不是继续挂公共表。
- 修正后的脑卒中专病链路应为：
  - 列表：`/api/vab/mbNzzSffw1mainZs/getList`
  - 明细：`/api/vab/mbNzzSffw1mainZs/get`
  - 保存：`/api/vab/mbNzzSffw1mainZs/doEdit`
  - 删除：`/api/vab/mbNzzSffw1mainZs/doDelete`
- 实现时可直接复用冠心病专病随访 controller/mapper 结构，主要替换：
  - 主表名：`NAOZUZHONG_SFFW`
  - namespace / controller 名称
  - `MBSF_YAOWU.ZHUBIAO_TYPE`：`NAOZUZHONG_SFFW`
- 脑卒中表单 `253161516000188` 也必须同步切换：
  - 明细回填用 `/api/vab/mbNzzSffw1mainZs/get`
  - 保存用 `/api/vab/mbNzzSffw1mainZs/doEdit`
  - 新建默认值写 `DAID`
  - 默认状态写 `DATA_STATE='1'`

## 2026-03-11 18:10:00

- 当前仓库里仍未发现慢阻肺“患者人员分级记录表”或对应后端接口；现有 `MbMzfFwb1mainZs` 对应的是套餐配置表 `MB_MZF_FWB_1MAIN_ZS`，不是患者分级记录表。
- 在正式数据表未确定前，慢阻肺人员分级先采用“前端确认版”策略：
  - 页面可正常新增、编辑、删除
  - 保存仅写入浏览器 `localStorage`
  - 以患者 `mbJbxxFid` 作为本地草稿分桶键
- 当前前端确认版字段范围已经收敛为：
  - 服务日期
  - 基本分类：`MZF_ICD`
  - 管理分类(M2)：`MB_MZF_FENLEI`
  - 预防分级(M3)：`MB_MZF_CQSF_FJGL`
  - 精细化管理：从 `MB_MZF_FWB_1MAIN_ZS` 按 `M2 + M3 + AVAILABLE_STATE=1` 过滤
  - 风险评估：`CAT评分 / mMRC评分 / FEV1占预计值 / 近一年急性加重次数`
  - 干预信息：`吸烟情况 / 长期家庭氧疗 / 无创通气`
  - 备注
- 后续一旦开始建表，建议直接按这套前端已确认字段反推表结构和接口，而不是再去套用冠心病/脑卒中的 `1zhkzmb` 表。

## 2026-03-11 19:05:00

- 慢阻肺人员分级后端现已按独立专病表模式补齐，接口前缀为：`/api/vab/mbMzf1zhkzmbZs`
- 结构采用两张表：
  - 主表：`MB_MZF_1ZHKZMB_ZS`
  - 动态字段明细表：`MB_MZF_ZHKZMB_ITEM_ZS`
- 当前确认的主表字段为：
  - `MB_JBXX_FID`
  - `SUIFANG_RQ`
  - `FANGHU_FENJI`
  - `CAT_SCORE`
  - `MMRC_SCORE`
  - `FEV1_PRED`
  - `FVC`
  - `JXZJ_CS`
  - `REMARK`
  - `SHENHE_ZT`
  - `SHENHE_RESULT`
  - `CREATE_TIME / CREATE_USER_FID / UPDATE_TIME / UPDATE_USER_FID`
- 当前确认的动态字段放在 `MB_MZF_ZHKZMB_ITEM_ZS`：
  - `MZF_ICD`
  - `guanliFenlei`
  - `fwbfid`
  - `xiyanQk`
  - `cqtyl`
  - `wctq`
- 设计约束：
  - 同一患者 `MB_JBXX_FID` 下，同一天 `SUIFANG_RQ` 只允许一条记录
  - 前端多选 `MZF_ICD` 当前按逗号字符串保存到 dynfield 明细，读取时再拆回数组
- 建表 SQL 已同步落到 `Learing`，表和字段备注全部为中文，可直接作为建库草稿起点。

## 2026-03-11 19:20:00

- 慢阻肺人员分级已补“慢阻肺疾病情况”模块，但当前仍按 dynfield 方案处理，不进入主表固定列。
- 当前疾病情况字段包括三组：
  - `mzfXbsSf / mzfXbsQt`：现病史开关与多选值
  - `mzfJwsSf / mzfJwsQt`：既往史开关与多选值
  - `mzfJzsSf / mzfJzsQt`：家族史开关与多选值
- 当前候选项先固定为：
  - `tnb`：糖尿病
  - `gxy`：高血压
  - `gxb`：冠心病
  - `nzz`：缺血性卒中(脑梗死)
  - `mzf`：慢阻肺(COPD)
- 这些字段暂不单独落主表列，避免把慢阻肺分级主表过早做成“通用共病信息表”；若后续业务确认需要做结构化统计，再考虑升级为正式列。 

## 2026-03-11 19:35:00

- 参照脑卒中结构，慢阻肺“疾病情况”已上提到主表 `MB_MZF_1ZHKZMB_ZS`，不再只停留在 dynfield。
- 当前主表新增 6 个字段：
  - `MZF_XBS_SF`
  - `MZF_XBS_QT`
  - `MZF_JWS_SF`
  - `MZF_JWS_QT`
  - `MZF_JZS_SF`
  - `MZF_JZS_QT`
- 页面仍保持当前交互形式，但保存时会把这 6 个字段同步写入主表；多选项仍按逗号串写入 `*_QT`。 

## 2026-03-30 11:21:01

- 高血压随访页的“控制目标 / 辅助检查”动态区块，后端保存并不是只看 `dynfield`；`FastMbGxySffw1mainZsServiceImpl#refreshOther()` 还强依赖：
  - `groups`: 分组编码数组
  - `streamsAsJson[groupCode]`: 该分组下的字段 rid 数组
  - `group_<groupCode>_is`: 分组启用状态
- 如果页面上能看到动态项、回填也正常，但保存后 `mb_gxy_sffw_2group / mb_gxy_sffw_3data` 没更新，优先检查请求体里的 `groups` 是否为空。仅有 `dynfield` 和 `group_*_is` 还不够，后端不会自行遍历所有动态字段。
- 这类动态组件（`vc_cndop01_numval / vc_cndop09_not_group` 等）虽然会在初始化时往 `form.groups` 和 `form[groupCode]` 注册分组，但父表单一旦 `resetForm/Object.assign`，运行期注册信息可能丢失。更稳的做法是在提交前根据当前 `kzzb/other` 配置重新构造一遍 `groups` 和每组字段数组，再提交给后端。~喵

## 2026-03-30 11:26:38

- 当高血压随访保存时报 `ORA-01400: 无法将 NULL 插入 MB_GXY_SFFW_3DATA.IS_SELECTED`，优先检查请求体里是否给每个动态字段带上了 `dynfield.is_<fieldCode>`。
- `FastMbGxySffw1mainZsServiceImpl#refreshOther()` 在遍历 `groups` 时会直接读取：
  - `dynfield[fieldCode]`
  - `dynfield["is_" + fieldCode]`
  并把后者写入 `MB_GXY_SFFW_3DATA.IS_SELECTED`；如果前端只补了 `groups` 和字段值，但没补 `is_<fieldCode>`，后端就会向非空列写 `NULL`。
- 对这类动态分组表单，提交前最稳妥的兜底是同时重建三类数据：
  - `groups`
  - `args[groupCode] = fieldCodes`
  - `args.dynfield["is_" + fieldCode] = '0' | '1'`~喵

## 2026-03-30 13:16:58

- 高血压随访这类动态分组表单，提交前兜底逻辑容易越修越重；如果问题已经定位到“后端只需要 `groups / groupCode / is_<fieldCode>`”，后续应优先把实现收敛成最小闭环，不要同时保留多套重复的分组重建分支，否则后面再查保存问题时会增加判断成本。~喵

## 2026-03-31 09:14:04

- 当前项目里的“转诊/会诊”虽然前端文件名和部分表名仍带 `Tnb`，但后端真正更通用的患者级链路已经存在：
  - 汇总表：`mb_jbxx_06zhuanzhen`
  - 原始单据：`zhuanzhen_dan / zhuanzhen_dan_xiazhuan`
  - 接口：`/api/vab/mbZhuanzhen/getZhuanzhenMq`、`/api/vab/mbZhuanzhen/getZhuanzhenMqList`
- 这条通用链路返回的核心字段是转诊状态、转诊类型、原始转诊单 fid、转出/转入机构和转诊日期，没有发现糖尿病特有业务字段；因此患者页“转诊/会诊”更适合直接切到通用链路，而不是继续为高血压/冠心病/脑卒中复制 `mbTnbTodoZhuanzhen*` 一套。~喵
- 如果要把患者页区分成“进行中的转诊”和“转诊历史”，基于 `mb_jbxx_06zhuanzhen` 最稳的前端做法是：
  - 先按 `zhuanzhen_type + zhuanzhen_dan_fid` 对记录做 latest 去重
  - 再按状态码区分 active / history
  - 不要直接把同一转诊单的所有状态流转记录原样都塞到“进行中”列表里。~喵
- 这次切通用链路时，最小前端闭环包括：
  - 患者首页卡片 `ReferralRecords.vue` 改走 `mbZhuanzhen`
  - `manbingkit.ts` 的 `80zhuanzhenLc / 80zhuanzhenLs` 改为通用路由
  - 新增通用患者页壳子 `hzinfo/zhuanzhen*`
  - 新建通用列表组件与查看弹窗，避免继续在糖尿病文件名下叠逻辑。~喵

## 2026-03-31 09:42:45

- `mb_jbxx_06zhuanzhen + zhuanzhen_dan/xiazhuan` 更适合做“患者转诊汇总/轨迹”，不适合直接替代旧 Vue2 的患者页“档案转诊记录【待办】”页面。~喵
- 判断标准很直接：如果某个页面需要这些能力，就不能只用 `mbZhuanzhen/getZhuanzhenMq` 来替代：
  - 状态筛选
  - 关键字搜索
  - 新建转诊申请
  - 取消转诊申请
  - 受理转诊
- 旧患者页 `YsMbTnbTodoZhuanzhenLcOneJbxxIndex.vue` 查的是 `MB_TNB_TODO_ZHUANZHEN_LC` 待办任务表；它和 `mb_jbxx_06zhuanzhen` 的职责不同。前者是任务页，后者是汇总轨迹页。即使患者相同，两边记录数和可操作能力也不必然一致。~喵
- 后续如果继续做通用转诊，建议分成两类页面：
  - 通用总览页：可以用 `mb_jbxx_06zhuanzhen`
  - 通用任务页：需要后端单独补“待办/历史任务”通用接口，不能只靠当前汇总接口硬顶。~喵

## 2026-03-31 10:00:41

- 如果个人首页里的“转诊/会诊”卡片只是做患者级摘要展示，而不是病种专属任务页，那么让高血压等病种先复用糖尿病这套患者转诊数据源是可接受的中间态。关键前提是：页面语义要明确为“这个患者的转诊记录”，而不是“这个病种的专属转诊记录”。~喵

## 2026-03-31 10:22:25

- 个人首页组件同名文件要先确认“当前生效的是哪一个版本”再改。当前 `ReferralRecords.vue` 实际已演进成“带弹窗列表版”，包含 `dialogComponent` 和 `openReferralDialog()`；如果只按旧的简版实现判断，就会出现“代码看起来已经给 GXY 加了映射，但页面仍然没有数据”的错位。遇到这种情况，优先重新读取当前文件内容，确认不是基于过期上下文在判断。~喵

## 2026-04-01 14:05:51

- “全部档案”这类跨病种列表新增筛选时，不能只在前端加 `el-select`：必须同时完成三段改造，筛选才会真实生效：
  - 前端 `query` 增加字段（例如 `manbingKind`）并随 `crud.toQuery()` 提交。
  - 服务层在 `SearchFormEx` 里 `removeByRealName("manbingKind")` 后 `form.put("manbingKind", manbingKind)` 透传给 MyBatis。
  - `MbJbxx02infoZs_Mapper.xml` 的公共条件块（如 `noWhere_cnd`）增加 `choose/when` 映射到 `mb_jbxx_05dqglxx` 的 `is_tnb/is_gxy/is_gxb/is_nzz/is_mzf/is_mxsb`。
- 对 `left join mb_jbxx_05dqglxx gl` 场景，建议用 `nvl(gl.is_xxx,'0')='1'`，可兼容没有管理记录的档案，不会因 `NULL` 造成筛选判断偏差。~喵

## 2026-04-01 14:31:30

- 对“前端请求体已携带筛选字段，但列表仍是全量”的场景，优先检查服务层是否把该字段真正写入 MyBatis 参数 `form`。本次 `queryAllDangan` 虽已在 XML 写好 `manbingKind` 条件，但因 Java 未 `form.put("manbingKind", ...)`，最终条件不会生效。~喵
- `SearchFormEx` 自定义字段在不同入口可能出现键名变体（驼峰/全小写/下划线）和数据形态变体（字符串/数组/列表）。对关键筛选参数建议统一做“多键名 + 首值提取 + 值标准化”兜底，再下传 SQL，能显著减少线上“看似传了参数但不生效”的问题。~喵

## 2026-04-01 14:42:10

- “全部档案”页如果要复用“新建档案”能力，最小闭环是 3 点：
  - 工具栏增加按钮并绑定 `handleInit()`；
  - 页面挂载 `Ys_MbJbxx02infoZsFormCreate` 组件实例；
  - 通过 `showNewItem("tnb")` 打开弹窗，并监听 `fetch-data` 回刷 `crud`。
- 这条链路不依赖 `crud` 的 add 按钮开关（`optShow.add` 可继续为 false），适合在只读列表里按需补充“快捷新建”入口。~喵

## 2026-04-01 14:52:20

- 列表筛选从单选升级到多选时，后端不能只改前端 `multiple`：必须把参数模型从“单值”同步升级为“多值集合 + 单值兜底”。本次采用 `manbingKinds`（主）+ `manbingKind`（兼容）双轨，能同时兼容新旧请求。~喵
- MyBatis 多值筛选推荐模式：`and (cond1 or cond2 ...)`，由 `foreach + choose` 生成；当多值为空时再回退到原单值条件，避免老页面和历史请求被破坏。~喵

## 2026-04-01 15:01:10

- “疾病多选筛选”在业务上通常需要先确认语义是并集(OR)还是交集(AND)。本场景应为交集：例如勾选“高血压+冠心病”时，只返回同时 `is_gxy=1` 且 `is_gxb=1` 的档案。~喵

## 2026-04-01 15:07:40

- 列表页同时有筛选和新建时，如果新建属于当前筛选语境的高频操作，建议把新建入口放到搜索按钮旁边，优先减少用户在“筛选→新建”间的鼠标移动成本。~喵

## 2026-04-01 15:13:40

- 对基于 `$route.params` 的 `watch` 做缺参校验时，要先判断“当前是否仍在目标路由”；否则用户离开页面时也会触发 watcher，造成误报弹窗。~喵

## 2026-04-01 15:32:20

- 当 Java 实体把时间字段声明为 `Date`，但线上 Oracle 列实际是 `VARCHAR2(14)`（如 `yyyyMMddHHmmss`）时，直接绑定会出现 ORA-12899。可在 MyBatis 层用 `to_char(#{time},''yyyymmddhh24miss'')` 统一落库格式，避免 JDBC 默认字符串化导致长度超限。~喵

## 2026-04-01 16:10:30

- “全部档案”右侧纳入操作较多时，可通过新增的“测试纳入”弹窗把六个病种纳入按钮集中展示，避免行内按钮过长导致定位困难。~喵
- 弹窗里的按钮可直接复用原 `doAddDanganToManbing(row, addtype)` 链路；已纳入病种显示禁用态，未纳入病种继续走 `el-popconfirm` 二次确认，能在不改后端的前提下快速验证纳入操作可用性。~喵
- “全部档案”操作列若已提供统一弹窗入口（如“测试纳入”），应移除行内重复的“纳入xxx管理”按钮，减少视觉噪音和误点。~喵
- 若业务要求“从列表直接编辑基本信息且不离开当前页”，可在列表页使用 `el-drawer` 挂载 `comp_jbxx_editor_form` 并调用 `show(rid)`，实现右侧抽屉式编辑；保存事件回刷列表即可形成闭环。~喵
- 抽屉式表单如需对齐视觉稿中的“中线偏左”位置，可将 `el-drawer size` 从 55% 提升到约 62%-65%，本次采用 64%。~喵
- 当抽屉承载密集表格表单时，`size=70%` 更利于减少字段折行和纵向滚动；本项目“编辑基本信息”场景可直接使用。~喵
- 对患者编辑页左侧病种导航，如果需要“只展示在管病种”，可在入口层增加“疾病管理”弹窗并基于 `isTnb/isGxy/isGxb/isNzz/isMzf/isMxsb` 动态过滤服务块；字段读取建议同时兼容主对象与 `da05dqglxxObj`。~喵
- 如果疾病管理入口要求在列表行级操作区，建议放在“编辑基本信息”同列并绑定当前行数据，弹窗内按 `isTnb/isGxy/isGxb/isNzz/isMzf/isMxsb` 过滤显示病种服务块。~喵
- 列表行级“疾病管理”如果需要在当前页内查看业务子页面，可采用“一级疾病弹窗 + 二级 iframe 弹窗”模式：一级负责病种过滤，二级负责承载具体业务页面。~喵
- 对“弹窗内嵌业务页”场景，可在 iframe load 后注入最小 CSS（隐藏 `.hzinfo-jbxxeditor__aside` 并去除主容器 padding）+ `scrollTo`，将视觉聚焦到业务表单区。~喵
- 若期望“和个人首页编辑入口一致”的分级弹窗，优先直接复用 `YsMb*Fenji*DanganIndex` 组件并调用 `show(jbxxid)`，而不是 iframe 整页路由。~喵
- “疾病管理”弹窗推荐双轨策略：优先复用 `*DanganIndex.vue`（可 `show(jbxxid)`）走组件弹窗；无可复用组件时再走 iframe 兜底，确保一致体验与功能完整性平衡。~喵
- 对不可组件化的业务页，可通过 iframe 注入“壳层隐藏 + 主卡片定位”样式实现与组件弹窗接近的统一体验，作为过渡方案。~喵
- 当同类业务页面已存在路由版本但缺少可复用“弹窗列表组件”时，可先落一层 `RouteEmbedDanganIndex` 作为统一承载基座，再用病种包装组件暴露 `show(jbxxid)`，实现“先组件化、后逐步去壳”的演进路径。~喵
- 对“列表弹窗体验不一致”入口，优先将业务页逻辑下沉为真正 `DanganIndex` 组件，而不是只包一层路由 iframe。~喵
- 当要求“所有病种入口都保持同款弹窗”时，建议为每个入口提供统一接口的原生 `DanganIndex` 组件（至少包含 `show(jbxxid)`），避免部分入口仍依赖路由壳导致体验不一致。~喵

## 2026-04-01 18:25:03

- 对“一级弹窗 -> 二级弹窗”的多层交互，关闭二级时不要只依赖当前行对象是否还存在。更稳的做法是进入二级前先缓存来源行快照，二级关闭时优先恢复快照再重开一级弹窗，避免回到底图。~喵
- 在列表刷新或组件重渲染后，行对象引用可能失效；为回跳链路增加 `row backup` 能显著降低“偶发不回一级弹窗”的问题。~喵

## 2026-04-01 18:28:35

- 列表场景如果需要“快速查看患者全貌”，建议在行级操作区提供“个人首页”直达入口，减少“编辑页再二次进入个人页”的点击路径。~喵
- 本项目个人首页标准路由为 `/hzinfo/personhome/{rid}`；在列表按钮点击时直接以当前行 `rid` 跳转即可，不需要额外拼病种参数。~喵

## 2026-04-01 18:30:06

- 如果业务希望“保留当前列表筛选上下文”，列表行跳转到个人页更适合新页签打开；实现上可用 `this.$router.resolve(path).href + window.open(href, '_blank')`，避免手写 hash 路径带来的 base 差异问题。~喵

## 2026-04-02 14:51:10

- 当“筛选项固定且数量少（如 6 个病种）”时，建议把下拉多选改为行内多选按钮（`el-checkbox-group + el-checkbox`），可减少一次点击层级并提升可见性。~喵
- 若需要保持原筛选能力不变，直接复用原数组字段（如 `query.manbingKind`）和 `@change="crud.toQuery()"` 即可，无需改后端参数结构。~喵

### 2026-04-02 20:10:00

- OGTT 页面迁移结论：Vue3 需同时补“历史组件名兼容 + 路径回退兼容（iews/ogtt/OGTTdbrw|OGTTywc/index.vue）”，否则后台菜单在不同配置下可能仍会 404。详见 KONWLEDGE/2026-04-02-ogtt-vue2-to-vue3-migration.md。~喵
- OGTT 路由兜底新增规则：nsureTnbRoutes 需要显式补 /tnb/ogtt 及其 OGTTdbrw/OGTTywc 子路由，避免后端路由配置不全时菜单消失或 404。~喵
- OGTT 路由兼容要覆盖顶层 #/ogtt/*（不仅是 /tnb/ogtt/*），建议在路由兜底中同时维护两套入口。~喵

## 2026-04-02 15:01:18

- “编辑基本信息->既往史->疾病”这块前端展示与后端在管标记不是同一层：
  - 既往史勾选与确诊年月保存到 `MB_JBXX_04JWS_JB`（字段 `JIBING`、`QUEZHEN_RQ`）。~喵
  - 在管慢病标记保存到 `MB_JBXX_05DQGLXX`（字段 `IS_TNB/IS_GXY/IS_GXB/IS_NZZ/IS_MZF/IS_MXSB`）。~喵
- 仅改前端勾选项不够，若新增“纳入慢病统计”病种，还需同步后端 `FastMbJbxx02infoZsServiceImpl#refreshOther` 的代码映射，否则不会写入 `MB_JBXX_05DQGLXX` 对应 `IS_*` 标记。~喵
- 目前这次已补齐 `04/05/07/12 -> IS_GXB/IS_MZF/IS_NZZ/IS_MXSB` 映射；数据库表字段已存在时不需要新增表结构。~喵
- 若希望“编辑页勾选慢病”直接驱动“列表测试纳入状态”，后端应按同一来源字段做全量回写（不仅置1，还要对未勾选项置0），否则会出现只能纳入不能取消的状态漂移。~喵

## 2026-04-02 20:56:00

- “全部档案”里的 `comp_jbxx_editor_form` 可以通过自定义事件把“保存前后慢病勾选变化”上抛给父页面，推荐事件载荷最少包含：`rid`、`mxsbCheckedBeforeSave`、`mxsbCheckedAfterSave`。~喵
- 慢性肾病联动可拆成两类动作：
  - 勾选：复用已有纳入接口 `changeEdit({ jbxxid, addtype: 'mxsb' })`，和“测试纳入”保持一致。~喵
  - 取消勾选：调用 `/api/vab/mbJbxx05dqglxx/doEdit` 并传 `rid + mbJbxxFid + isMxsb:'0'`，即可取消在管标记。~喵
- 对“勾选变化判断”建议抽成纯函数（如 `resolveMxsbSyncAction`），再配一条最小单测覆盖 `add/remove/none`，便于后续扩展到其他病种。~喵
- 在实际业务中，联动触发建议优先以“保存后最终勾选状态”做幂等同步（勾选=纳入，未勾选=取消），比“仅在前后变化时才触发”更稳，能规避表单回填/初始化差异导致的漏触发。~喵

## 2026-04-02 16:45:00

- 对“保存后联动纳入/取消”场景，事件载荷中的勾选状态可能受前端渲染时序影响；更稳的做法是在父页面按 `rid` 二次读取后端最新 `jbxx`，再做联动决策。~喵
- 解析 `jiwangSjb` 时要兼容两种形态：`"02,03,12"`（字符串）和 `['02','03','12']`（数组）；统一归一化后再 `includes('12')` 可避免误判。~喵
- 当前慢性肾病联动链路建议：`保存基本信息 -> get(jbxx)判定12 -> add(changeEdit)/remove(doEdit)`，可提升取消纳入稳定性。~喵

## 2026-04-02 17:11:15

- “编辑基本信息 -> 既往史 -> 纳入慢病统计”和 `MB_JBXX_05DQGLXX` 六个在管标记，本质上应按一次保存后的最终勾选结果做全量覆盖，不要继续拆成单病种零散联动。~喵
- 当前项目六病种映射关系固定为：
  - `02 -> isGxy`
  - `03 -> isTnb`
  - `04 -> isGxb`
  - `05 -> isMzf`
  - `07 -> isNzz`
  - `12 -> isMxsb`
- 推荐把这层规则抽成纯函数统一维护，例如 `buildManbingSyncPayload(jiwangSjb)`，直接产出 `isGxy/isTnb/isGxb/isMzf/isNzz/isMxsb` 的 `1/0` 载荷。~喵
- 如果患者还没有 `MB_JBXX_05DQGLXX` 记录，又需要把某些病种同步成 `1`，仅调用 `doEdit` 直接置字段不一定稳；更稳的前端链路是：
  - 先判断六病种是否至少有一个为 `1`
  - 有则先复用已有 `changeEdit({ jbxxid, addtype })` 创建首条记录
  - 再调用 `/api/vab/mbJbxx05dqglxx/doEdit` 一次性全量覆盖六病种状态
- 若六病种最终全部为 `0` 且数据库本来就没有 `MB_JBXX_05DQGLXX` 记录，可直接跳过创建，避免生成纯空白管理记录。~喵

## 2026-04-02 17:21:25

- `/api/vab/mbJbxx05dqglxx/doEdit` 不是纯粹只更新 `MB_JBXX_05DQGLXX`；它会进入 `FastMbJbxx05dqglxxServiceImpl#doSaveIt -> refreshOther()` 的通用保存链。~喵
- 当前实现里 `refreshOther()` 还残留了一段历史药品关联逻辑，会操作 `FK_YTLK_HISTORY_YAO_JL`。如果库里没有这张表，而接口更新场景又无条件执行删除，就会报 `ORA-00942`。~喵
- 结论：对这种“当前实体已迁移、但 service 里还带历史附表逻辑”的接口，新增前端调用前要先检查 `refreshOther()` 是否会碰到无关表。~喵
- 更安全的写法是：只有在请求体实际带了对应业务数据（例如 `yaopin`）时，才执行附表删改；不要因为一次普通字段更新就无条件访问历史附表。~喵

## 2026-04-02 17:37:15

- 列表行级弹窗如果只依赖当前行已返回的状态字段，不要为了“最新数据”在打开前额外串行或并行拉详情；更好的交互是先秒开，再按需后台刷新。~喵
- “全部档案 -> 疾病管理”当前只依赖 `row.da05dqglxxObj.isTnb/isGxy/isGxb/isNzz/isMzf/isMxsb` 来过滤入口，而这些字段列表接口已经带回，所以打开前再次请求 `jbxx/get` 与 `mbJbxx05dqglxx/getList` 属于不必要阻塞。~喵
- 对这种“列表按钮 -> 轻量过滤弹窗”场景，推荐直接缓存当前行快照并立即展示；只有当弹窗内容确实需要更完整详情时，才考虑在弹窗内部异步补拉。~喵

## 2026-04-23

- `form_create` 旧表单迁移到 `as_vite` 时，不能只复制 `rule.json / options.json`；还需要同时补齐两层运行时能力：一层是旧脚本全局函数，例如 `shanshizhidao_kit.js` 里的 `computeHb / GaoXueYa / syncTopAndTrigger / woLiFenji`，另一层是旧设计器字符串回调解析，例如 `[[FORM-CREATE-PREFIX-function ... -FORM-CREATE-SUFFIX]]` 和 `$FNX:`。否则表单能渲染，但联动、onMounted、onSubmit、字段 change/mounted 逻辑不会真正执行。喵
