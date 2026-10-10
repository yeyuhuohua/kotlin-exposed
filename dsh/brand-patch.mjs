import fs from 'node:fs'

const root = '/usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai'

function replace(file, from, to) {
  const path = `${root}/${file}`
  const source = fs.readFileSync(path, 'utf8')
  if (!source.includes(from)) throw new Error(`pattern missing in ${file}: ${from.slice(0, 80)}`)
  fs.writeFileSync(path, source.replace(from, to))
}

replace(
  'dsh-client-ui-layout/lib/client.js',
  'const productTitle = "DeepSeek Harness";',
  'const productTitle = "人事工作台";',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  '描述你想要构建的内容, / 调用指令, @ 文件或对话',
  '问人事数据，例如现在有多少名员工',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  '"hero.headline": "探索未至之境"',
  '"hero.headline": "人事工作台"',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  '"hero.headline": "Into the Unknown"',
  '"hero.headline": "HR Workspace"',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  '"hero.preview": "预览版"',
  '"hero.preview": "本地"',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  'function HeroFish({ hovering }) {\n\t\t\treturn (0, react_jsx_runtime.jsx)("svg", {',
  'function HeroFish({ hovering }) {\n\t\t\treturn (0, react_jsx_runtime.jsx)("span", { style: { fontWeight: 700, fontSize: 22, letterSpacing: "0.04em", lineHeight: 1 }, children: "HR" });\n\t\t\treturn (0, react_jsx_runtime.jsx)("svg", {',
)
replace(
  'dsh-client-ui-conversation/lib/client.js',
  'Describe what you want to build, / commands, @ files or sessions',
  'Ask about HR data, for example how many employees there are',
)
replace(
  'dsh-client-ui-brand-official/lib/client.js',
  'return (0, react_jsx_runtime.jsx)(_deepseek_ai_dsh_client_ui_primitives.FishLogo, { size });',
  'return (0, react_jsx_runtime.jsx)("span", { style: { fontWeight: 700, fontSize: Math.max(12, (size ?? 24) * 0.55), letterSpacing: "0.04em" }, children: "HR" });',
)
replace(
  'dsh-client-ui-brand-official/lib/client.js',
  'return (0, react_jsx_runtime.jsx)(_deepseek_ai_dsh_client_ui_primitives.BrandWordmark, { includeMark: false });',
  'return (0, react_jsx_runtime.jsx)("span", { style: { fontWeight: 650 }, children: "人事工作台" });',
)
