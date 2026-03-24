/* METADATA
{
    "name": "Automatic_ui_base",
    "description": { "zh": "提供基本的UI自动化工具，能够按照用户的要求帮助操作设备屏幕（如点击、滑动、输入等）。", "en": "Basic UI automation tools to operate the device screen as requested (tap, swipe, input, etc.)." },
    "enabledByDefault": true,
    "tools": [
        {
            "name": "usage_advice",
            "description": { "zh": "UI自动化核心原则（非常重要，请务必遵守）：\n\n【必须使用UI自动化的场景】\n当用户要求以下操作时，必须使用UI自动化工具（tap/click_element/swipe等），而非网络搜索或爬虫工具：\n1. 打开某个应用并在其中进行操作（如\"打开微信发消息\"、\"打开抖音刷视频\"）\n2. 访问网站并与之交互（如\"打开Chrome访问x.com并登录\"、\"在浏览器中打开网页并点击\"）\n3. 查看应用内的内容（如\"查看Twitter/X的最新推文\"、\"查看Instagram的通知\"）\n4. 在应用内导航（如\"打开设置并关闭WiFi\"、\"进入相册删除照片\"）\n5. 操作手机屏幕上的任何元素\n\n【关键区分】\n- 用户说\"检查x.com/ Twitter/ Instagram的通知\" → 是指打开对应APP查看应用内通知，不是系统通知！\n- 用户说\"打开Chrome去x.com\" → 必须用app_launch启动Chrome，然后用UI自动化工具点击地址栏、输入URL、导航\n- 只有当用户明确要求\"搜索网页\"、\"查资料\"、\"获取新闻\"时才使用网络搜索工具\n\n【操作流程】\n1. 先用 app_launch 启动目标应用\n2. 用 get_page_info 获取当前页面UI结构\n3. 用 click_element/tap 点击需要的元素\n4. 用 set_input_text 输入内容\n5. 用 swipe 滚动页面\n6. 重复步骤2-5直到完成任务\n\n【元素定位技巧】\n- 列表：使用index参数（例如，\"点击索引为2的列表项\"）\n- 文本：使用partialMatch进行模糊匹配（例如，\"点击包含'登录'文字的按钮\"）\n- 坐标：使用tap(x, y)进行精确点击\n\n【重要】组合调用：强烈建议在一次响应中组合调用2~3个工具，例如：app_launch → get_page_info → click_element。软件会自动按顺序执行。", "en": "UI AUTOMATION CORE RULES (CRITICAL - MUST READ):\n\n【WHEN TO USE UI AUTOMATION】\nYou MUST use UI automation tools (tap/click_element/swipe/etc.) instead of web search/scraping tools when user asks for:\n1. Opening an app and doing something inside it (e.g., \"open WeChat and send message\", \"open TikTok and browse videos\")\n2. Visiting websites and interacting with them (e.g., \"open Chrome and go to x.com and login\", \"open browser and click on elements\")\n3. Checking in-app content (e.g., \"check Twitter/X's latest tweets\", \"check Instagram's notifications\")\n4. Navigating within apps (e.g., \"open Settings and turn off WiFi\", \"go to gallery and delete photos\")\n5. Operating any elements on the phone screen\n\n【KEY DISTINCTIONS】\n- User says \"check x.com/Twitter/Instagram notifications\" → means open the APP and check in-app notifications, NOT system notifications!\n- User says \"open Chrome and go to x.com\" → MUST use app_launch to start Chrome, then use UI automation to click address bar, enter URL, navigate\n- Only use web search tools when user explicitly asks \"search the web\", \"get information\", \"find news\"\n\n【WORKFLOW】\n1. First use app_launch to start the target app\n2. Use get_page_info to get current page UI structure\n3. Use click_element/tap to click needed elements\n4. Use set_input_text to enter content\n5. Use swipe to scroll the page\n6. Repeat steps 2-5 until task is complete\n\n【Element Targeting】\n- Lists: use index parameter (e.g., tap list item at index 2)\n- Text: use partialMatch for fuzzy matching (e.g., tap button containing 'Login')\n- Coordinates: use tap(x, y) for precise clicks\n\n【IMPORTANT】Combined calls: strongly recommend combining 2~3 tools in one response, e.g. app_launch → get_page_info → click_element. The system will execute sequentially." },
            "parameters": [],
            "advice": true
        },
        {
            "name": "app_launch",
            "description": { "zh": "根据应用包名直接启动应用。如果未找到该包名对应的应用，则返回当前设备的软件安装列表，供你选择其他应用。\n\n【重要】当用户要求\"打开XX应用\"、\"去XX网站\"时，必须首先使用此工具启动应用，然后使用其他UI自动化工具进行后续操作。", "en": "Launch an app by package name. If not found, returns the installed app list for you to choose from.\n\n【IMPORTANT】When user asks \"open XX app\" or \"go to XX website\", you MUST first use this tool to launch the app, then use other UI automation tools for subsequent operations." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名，例如'com.tencent.mm'（微信）、'com.twitter.android'（Twitter）、'com.instagram.android'（Instagram）、'com.android.chrome'（Chrome）、'com.brave.browser'（Brave）", "en": "App package name, e.g. 'com.tencent.mm' (WeChat), 'com.twitter.android' (Twitter), 'com.instagram.android' (Instagram), 'com.android.chrome' (Chrome), 'com.brave.browser' (Brave)." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "get_page_info",
            "description": { "zh": "获取当前UI屏幕的信息，包括完整的UI层次结构。\n\n【重要】每次执行UI操作后都应该调用此工具来获取更新后的页面状态，以便进行下一步操作。", "en": "Get information about the current UI screen, including the full UI hierarchy.\n\n【IMPORTANT】You should call this tool after each UI operation to get the updated page state for the next operation." },
            "parameters": [
                { "name": "format", "description": { "zh": "格式，可选：'xml'或'json'，默认'xml'", "en": "Format: 'xml' or 'json' (default: 'xml')." }, "type": "string", "required": false },
                { "name": "detail", "description": { "zh": "详细程度，可选：'minimal'、'summary'或'full'，默认'summary'", "en": "Detail level: 'minimal', 'summary', or 'full' (default: 'summary')." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "get_page_screenshot_image",
            "description": { "zh": "获取当前屏幕内容的图片版本（截图），返回保存路径。用于直观查看当前屏幕状态。", "en": "Capture the current screen as an image (screenshot) and return the saved file path. Use to visually check current screen state." },
            "parameters": []
        },
        {
            "name": "tap",
            "description": { "zh": "在特定坐标模拟点击。用于精确点击屏幕上的某个位置。", "en": "Simulate a tap at the specified coordinates. Use for precise clicks on screen positions." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "double_tap",
            "description": { "zh": "在特定坐标模拟双击（快速连续点击两次）。用于放大图片等操作。", "en": "Simulate a double tap at the specified coordinates (two quick taps). Use for zooming images, etc." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "long_press",
            "description": { "zh": "在特定坐标模拟长按操作。适用于呼出上下文菜单、拖拽、长按选择等场景。", "en": "Simulate a long press at the specified coordinates. Useful for context menus, dragging, long-press selection, etc." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "click_element",
            "description": { "zh": "点击由资源ID、类名或文本标识的元素。通过UI层次结构定位元素，比坐标点击更可靠。\n\n【使用场景】\n- 点击按钮：提供className='android.widget.Button'或resourceId\n- 点击列表项：提供className和index\n- 点击包含特定文字的元素：使用partialMatch=true", "en": "Click an element identified by resourceId, className, or text. Locates elements through UI hierarchy, more reliable than coordinate taps.\n\n【Usage】\n- Click button: provide className='android.widget.Button' or resourceId\n- Click list item: provide className and index\n- Click element with specific text: use partialMatch=true" },
            "parameters": [
                { "name": "resourceId", "description": { "zh": "元素资源ID", "en": "Element resourceId." }, "type": "string", "required": false },
                { "name": "className", "description": { "zh": "元素类名", "en": "Element class name." }, "type": "string", "required": false },
                { "name": "index", "description": { "zh": "要点击的匹配元素，从0开始计数，默认0", "en": "Index of the matched element to click (0-based, default: 0)." }, "type": "number", "required": false },
                { "name": "partialMatch", "description": { "zh": "是否启用部分匹配（模糊匹配），默认false。设为true可匹配包含指定文字的元素。", "en": "Enable partial match (fuzzy matching), default: false. Set to true to match elements containing specified text." }, "type": "boolean", "required": false },
                { "name": "bounds", "description": { "zh": "元素边界，格式为'[left,top][right,bottom]'", "en": "Element bounds in format '[left,top][right,bottom]'." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "set_input_text",
            "description": { "zh": "在输入字段中设置文本。在使用此工具前，应先点击输入框使其获得焦点。", "en": "Set text in the current input field. Before using this, click on the input field to give it focus." },
            "parameters": [
                { "name": "text", "description": { "zh": "要输入的文本", "en": "Text to input." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "press_key",
            "description": { "zh": "模拟按键。用于返回、主页、任务切换等系统操作。", "en": "Simulate a key press. Use for system operations like back, home, task switch." },
            "parameters": [
                { "name": "key_code", "description": { "zh": "键码，例如'KEYCODE_BACK'（返回）、'KEYCODE_HOME'（主页）、'KEYCODE_APP_SWITCH'（任务切换）、'KEYCODE_ENTER'（确认）", "en": "Key code, e.g. 'KEYCODE_BACK' (back), 'KEYCODE_HOME' (home), 'KEYCODE_APP_SWITCH' (task switch), 'KEYCODE_ENTER' (confirm)." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "swipe",
            "description": { "zh": "模拟滑动手势。用于滚动页面、上滑、下滑、左滑、右滑等操作。", "en": "Simulate a swipe gesture. Use for scrolling pages, swipe up/down/left/right." },
            "parameters": [
                { "name": "start_x", "description": { "zh": "起始X坐标", "en": "Start X coordinate." }, "type": "number", "required": true },
                { "name": "start_y", "description": { "zh": "起始Y坐标", "en": "Start Y coordinate." }, "type": "number", "required": true },
                { "name": "end_x", "description": { "zh": "结束X坐标", "en": "End X coordinate." }, "type": "number", "required": true },
                { "name": "end_y", "description": { "zh": "结束Y坐标", "en": "End Y coordinate." }, "type": "number", "required": true },
                { "name": "duration", "description": { "zh": "持续时间，毫秒，默认300", "en": "Duration in milliseconds (default: 300)." }, "type": "number", "required": false }
            ]
        }
    ]
}
*/
const UIAutomationTools = (function () {
    async function get_page_info(params) {
        const result = (await UINode.getCurrentPage()).toFormattedString();
        return { success: true, message: '成功获取页面信息', data: result };
    }
    async function get_page_screenshot_image(params) {
        try {
            const screenshotDir = OPERIT_CLEAN_ON_EXIT_DIR;
            // Ensure the directory exists
            await Tools.Files.mkdir(screenshotDir, true);
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filePath = `${screenshotDir}/ui_screenshot_${timestamp}.png`;
            console.log(`截取当前UI屏幕并保存到: ${filePath}`);
            const result = await Tools.System.shell(`screencap -p ${filePath}`);
            const imageLink = NativeInterface.registerImageFromPath(filePath);
            return {
                success: true,
                message: `截图已保存到 ${filePath}`,
                data: {
                    file_path: filePath,
                    image_link: imageLink,
                    raw_result: result,
                },
            };
        }
        catch (error) {
            console.error(`获取屏幕截图失败: ${error.message}`);
            return {
                success: false,
                message: `获取屏幕截图失败: ${error.message}`,
            };
        }
    }
    async function tap(params) {
        const result = await Tools.UI.tap(params.x, params.y);
        return { success: true, message: '点击操作成功', data: result };
    }
    async function double_tap(params) {
        const first = await Tools.UI.tap(params.x, params.y);
        await Tools.System.sleep(120);
        const second = await Tools.UI.tap(params.x, params.y);
        return {
            success: true,
            message: '双击操作成功',
            data: { first, second },
        };
    }
    async function long_press(params) {
        const result = await Tools.UI.longPress(params.x, params.y);
        return { success: true, message: '长按操作成功', data: result };
    }
    async function click_element(params) {
        const result = await Tools.UI.clickElement(params);
        return { success: true, message: '点击元素操作成功', data: result };
    }
    async function set_input_text(params) {
        const result = await Tools.UI.setText(params.text);
        return { success: true, message: '输入文本操作成功', data: result };
    }
    async function press_key(params) {
        const result = await Tools.UI.pressKey(params.key_code);
        return { success: true, message: '按键操作成功', data: result };
    }
    async function swipe(params) {
        const result = await Tools.UI.swipe(params.start_x, params.start_y, params.end_x, params.end_y);
        return { success: true, message: '滑动操作成功', data: result };
    }
    async function app_launch(params) {
        if (!params.package_name) {
            return { success: false, message: '必须提供package_name参数' };
        }
        try {
            const startResult = await Tools.System.startApp(params.package_name);
            if (startResult && startResult.success) {
                return {
                    success: true,
                    message: '应用启动成功',
                    data: {
                        operation: startResult,
                    },
                };
            }
            const appList = await Tools.System.listApps(false);
            return {
                success: false,
                message: '未能启动应用，可能未安装或无法找到启动入口。已返回当前安装的应用列表。',
                data: {
                    operation: startResult,
                    installed_apps: appList,
                },
            };
        }
        catch (error) {
            console.error(`app_launch 执行失败: ${error.message}`);
            try {
                const appList = await Tools.System.listApps(false);
                return {
                    success: false,
                    message: `启动应用时发生错误: ${error.message}。已返回当前安装的应用列表。`,
                    data: {
                        installed_apps: appList,
                    },
                };
            }
            catch (listError) {
                console.error(`获取应用列表失败: ${listError.message}`);
                return {
                    success: false,
                    message: `启动应用失败且无法获取应用列表: ${listError.message}`,
                };
            }
        }
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        console.log("=== UI Automation Tools 测试开始 ===\n");
        const results = [];
        try {
            // 1. 测试 get_page_info
            console.log("1. 测试 get_page_info...");
            const pageInfoResult = await get_page_info({});
            results.push({ tool: 'get_page_info', result: pageInfoResult });
            console.log("✓ get_page_info 测试完成\n");
            // 2. 测试 tap (点击屏幕中心位置)
            console.log("2. 测试 tap...");
            const tapResult = await tap({ x: 500, y: 1000 });
            results.push({ tool: 'tap', result: tapResult });
            console.log("✓ tap 测试完成\n");
            await Tools.System.sleep(500);
            // 3. 测试 press_key (按音量上键)
            console.log("3. 测试 press_key...");
            const pressKeyResult = await press_key({ key_code: 'KEYCODE_VOLUME_UP' });
            results.push({ tool: 'press_key', result: pressKeyResult });
            console.log("✓ press_key 测试完成\n");
            await Tools.System.sleep(500);
            // 4. 测试 set_input_text
            console.log("4. 测试 set_input_text...");
            const setTextResult = await set_input_text({ text: 'UI自动化测试文本' });
            results.push({ tool: 'set_input_text', result: setTextResult });
            console.log("✓ set_input_text 测试完成\n");
            await Tools.System.sleep(500);
            // 5. 测试 swipe (向上滑动)
            console.log("5. 测试 swipe...");
            const swipeResult = await swipe({
                start_x: 500,
                start_y: 1500,
                end_x: 500,
                end_y: 500,
                duration: 300
            });
            results.push({ tool: 'swipe', result: swipeResult });
            console.log("✓ swipe 测试完成\n");
            await Tools.System.sleep(500);
            // 6. 测试 click_element (尝试点击一个常见的元素)
            console.log("6. 测试 click_element...");
            try {
                const clickResult = await click_element({
                    className: 'android.widget.Button',
                    index: 0
                });
                results.push({ tool: 'click_element', result: clickResult });
                console.log("✓ click_element 测试完成\n");
            }
            catch (error) {
                console.log("⚠ click_element 测试失败（这可能是正常的，如果当前页面没有按钮）:", error.message, "\n");
                results.push({ tool: 'click_element', result: { success: false, message: error.message } });
            }
            console.log("=== UI Automation Tools 测试完成 ===\n");
            console.log("测试结果汇总:");
            results.forEach((r, i) => {
                const status = r.result.success ? '✓' : '✗';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });
            complete({
                success: true,
                message: "所有UI工具测试完成",
                data: results
            });
        }
        catch (error) {
            console.error("测试过程中发生错误:", error);
            complete({
                success: false,
                message: `测试失败: ${error.message}`,
                data: results
            });
        }
    }
    return {
        get_page_info: (params) => wrapToolExecution(get_page_info, params),
        app_launch: (params) => wrapToolExecution(app_launch, params),
        get_page_screenshot_image: () => wrapToolExecution(get_page_screenshot_image, {}),
        tap: (params) => wrapToolExecution(tap, params),
        double_tap: (params) => wrapToolExecution(double_tap, params),
        long_press: (params) => wrapToolExecution(long_press, params),
        click_element: (params) => wrapToolExecution(click_element, params),
        set_input_text: (params) => wrapToolExecution(set_input_text, params),
        press_key: (params) => wrapToolExecution(press_key, params),
        swipe: (params) => wrapToolExecution(swipe, params),
        main,
    };
})();
exports.get_page_info = UIAutomationTools.get_page_info;
exports.app_launch = UIAutomationTools.app_launch;
exports.get_page_screenshot_image = UIAutomationTools.get_page_screenshot_image;
exports.tap = UIAutomationTools.tap;
exports.double_tap = UIAutomationTools.double_tap;
exports.long_press = UIAutomationTools.long_press;
exports.click_element = UIAutomationTools.click_element;
exports.set_input_text = UIAutomationTools.set_input_text;
exports.press_key = UIAutomationTools.press_key;
exports.swipe = UIAutomationTools.swipe;
exports.main = UIAutomationTools.main;
