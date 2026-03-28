/* METADATA
{
    "name": "Automatic_ui_base",
    "description": { "zh": "提供基本的UI自动化工具，能够按照用户的要求帮助操作设备屏幕（如点击、滑动、输入等）。", "en": "Basic UI automation tools to operate the device screen as requested (tap, swipe, input, etc.)." },
    "enabledByDefault": true,
    "tools": [
        {
            "name": "usage_advice",
            "description": { "zh": "UI自动化核心原则（非常重要，请务必遵守）：\n\n【必须使用UI自动化的场景】\n当用户要求以下操作时，必须使用UI自动化工具（tap/click_element/swipe等），而非网络搜索或爬虫工具：\n1. 打开某个应用并在其中进行操作（如\"打开微信发消息\"、\"打开抖音刷视频\"）\n2. 访问网站并与之交互（如\"打开Chrome访问x.com并登录\"、\"在浏览器中打开网页并点击\"）\n3. 查看应用内的内容（如\"查看Twitter/X的最新推文\"、\"查看Instagram的通知\"）\n4. 在应用内导航（如\"打开设置并关闭WiFi\"、\"进入相册删除照片\"）\n5. 操作手机屏幕上的任何元素\n\n【关键区分】\n- 用户说\"检查x.com/ Twitter/ Instagram的通知\" → 是指打开对应APP查看应用内通知，不是系统通知！\n- 用户说\"打开Chrome去x.com\" → 必须用app_launch启动Chrome，然后用UI自动化工具点击地址栏、输入URL、导航\n- 只有当用户明确要求\"搜索网页\"、\"查资料\"、\"获取新闻\"时才使用网络搜索工具\n\n【重要执行规则】（必须严格遵守）\n1. 每次只执行一个UI操作 - 等待截图结果后再进行下一个操作\n2. 使用任何UI自动化工具（tap, long_press, swipe, click_element, set_input_text, press_key）后，必须立即使用capture_screenshot获取截图查看结果\n3. 严禁将工具语法或参数作为纯文本输出 - 必须通过工具调用界面实际调用工具\n4. 正确的操作流程：app_launch → tap/click_element → capture_screenshot → 分析截图 → 下一个操作", "en": "UI AUTOMATION CORE RULES (CRITICAL - MUST READ):\n\n【WHEN TO USE UI AUTOMATION】\nYou MUST use UI automation tools (tap/click_element/swipe/etc.) instead of web search/scraping tools when user asks for:\n1. Opening an app and doing something inside it (e.g., \"open WeChat and send message\", \"open TikTok and browse videos\")\n2. Visiting websites and interacting with them (e.g., \"open Chrome and go to x.com and login\", \"open browser and click on elements\")\n3. Checking in-app content (e.g., \"check Twitter/X's latest tweets\", \"check Instagram's notifications\")\n4. Navigating within apps (e.g., \"open Settings and turn off WiFi\", \"go to gallery and delete photos\")\n5. Operating any elements on the phone screen\n\n【KEY DISTINCTIONS】\n- User says \"check x.com/Twitter/Instagram notifications\" → means open the APP and check in-app notifications, NOT system notifications!\n- User says \"open Chrome and go to x.com\" → MUST use app_launch to start Chrome, then use UI automation to click address bar, enter URL, navigate\n- Only use web search when user explicitly says \"search the web\", \"look up information\", \"get news\"\n\n【CRITICAL EXECUTION RULES】 (MUST STRICTLY FOLLOW)\n1. Execute ONLY ONE UI action at a time - wait for screenshot result before proceeding to next action\n2. After ANY UI automation tool (tap, long_press, swipe, click_element, set_input_text, press_key), you MUST immediately take a screenshot using capture_screenshot to see the result\n3. NEVER output tool syntax or parameters as plain text - you MUST actually CALL the tools via tool calling interface\n4. Correct workflow: app_launch → tap/click_element → capture_screenshot → analyze screenshot → next action" },
            "parameters": [],
            "advice": true
        },
        {
            "name": "app_launch",
            "description": { "zh": "根据应用包名直接启动应用。如果未找到该包名对应的应用，则返回当前设备的软件安装列表，供你选择其他应用。", "en": "Launch an app by package name. If not found, returns the installed app list for you to choose from." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名，例如'com.tencent.mm'", "en": "App package name, e.g. 'com.tencent.mm'." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "get_page_info",
            "description": { "zh": "获取当前UI屏幕的信息，包括完整的UI层次结构。", "en": "Get information about the current UI screen, including the full UI hierarchy." },
            "parameters": [
                { "name": "format", "description": { "zh": "格式，可选：'xml'或'json'，默认'xml'", "en": "Format: 'xml' or 'json' (default: 'xml')." }, "type": "string", "required": false },
                { "name": "detail", "description": { "zh": "详细程度，可选：'minimal'、'summary'或'full'，默认'summary'", "en": "Detail level: 'minimal', 'summary', or 'full' (default: 'summary')." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "get_page_screenshot_image",
            "description": { "zh": "获取当前屏幕内容的图片版本（截图），返回保存路径。", "en": "Capture the current screen as an image (screenshot) and return the saved file path." },
            "parameters": []
        },
        {
            "name": "tap",
            "description": { "zh": "在特定坐标模拟点击。\n\n【重要】每次点击后必须使用capture_screenshot获取截图查看结果。", "en": "Simulate a tap at the specified coordinates.\n\n【IMPORTANT】After tapping, you MUST use capture_screenshot to see the result." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "double_tap",
            "description": { "zh": "在特定坐标模拟双击（快速连续点击两次）。\n\n【重要】每次双击后必须使用capture_screenshot获取截图查看结果。", "en": "Simulate a double tap at the specified coordinates (two quick taps).\n\n【IMPORTANT】After double tapping, you MUST use capture_screenshot to see the result." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "long_press",
            "description": { "zh": "在特定坐标模拟长按操作。适用于呼出上下文菜单、拖拽、长按选择等场景。\n\n【重要】每次长按后必须使用capture_screenshot获取截图查看结果。", "en": "Simulate a long press at the specified coordinates. Useful for context menus, dragging, long-press selection, etc.\n\n【IMPORTANT】After long pressing, you MUST use capture_screenshot to see the result." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "click_element",
            "description": { "zh": "点击由资源ID、类名或文本标识的元素。通过UI层次结构定位元素，比坐标点击更可靠。\n\n【使用场景】\n- 点击按钮：提供className='android.widget.Button'或resourceId\n- 点击列表项：提供className和index\n- 点击包含特定文字的元素：使用partialMatch=true\n\n【重要】每次点击后必须使用capture_screenshot获取截图查看结果。", "en": "Click an element identified by resourceId, className, or text. Locates elements through UI hierarchy, more reliable than coordinate taps.\n\n【Usage】\n- Click button: provide className='android.widget.Button' or resourceId\n- Click list item: provide className and index\n- Click element with specific text: use partialMatch=true\n\n【IMPORTANT】After clicking, you MUST use capture_screenshot to see the result." },
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
            "description": { "zh": "在输入字段中设置文本。在使用此工具前，应先点击输入框使其获得焦点。\n\n【重要】每次输入后必须使用capture_screenshot获取截图查看结果。", "en": "Set text in the current input field. Before using this, click on the input field to give it focus.\n\n【IMPORTANT】After inputting text, you MUST use capture_screenshot to see the result." },
            "parameters": [
                { "name": "text", "description": { "zh": "要输入的文本", "en": "Text to input." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "press_key",
            "description": { "zh": "模拟按键。用于返回、主页、任务切换等系统操作。\n\n【重要】每次按键后必须使用capture_screenshot获取截图查看结果。", "en": "Simulate a key press. Use for system operations like back, home, task switch.\n\n【IMPORTANT】After pressing key, you MUST use capture_screenshot to see the result." },
            "parameters": [
                { "name": "key_code", "description": { "zh": "键码，例如'KEYCODE_BACK'（返回）、'KEYCODE_HOME'（主页）、'KEYCODE_APP_SWITCH'（任务切换）、'KEYCODE_ENTER'（确认）", "en": "Key code, e.g. 'KEYCODE_BACK' (back), 'KEYCODE_HOME' (home), 'KEYCODE_APP_SWITCH' (task switch), 'KEYCODE_ENTER' (confirm)." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "swipe",
            "description": { "zh": "模拟滑动手势。用于滚动页面、上滑、下滑、左滑、右滑等操作。\n\n【重要】每次滑动后必须使用capture_screenshot获取截图查看结果。", "en": "Simulate a swipe gesture. Use for scrolling pages, swipe up/down/left/right.\n\n【IMPORTANT】After swiping, you MUST use capture_screenshot to see the result." },
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

import { Tool, ToolResponse } from './tool-types';

interface ToolParams {
    format?: 'xml' | 'json';
    detail?: 'minimal' | 'summary' | 'full';
}

interface TapParams {
    x: number;
    y: number;
}

interface DoubleTapParams {
    x: number;
    y: number;
}

interface LongPressParams {
    x: number;
    y: number;
}

interface ClickElementParams {
    resourceId?: string;
    className?: string;
    index?: number;
    partialMatch?: boolean;
    bounds?: string;
}

interface SetInputTextParams {
    text: string;
}

interface PressKeyParams {
    key_code: string;
}

interface SwipeParams {
    start_x: number;
    start_y: number;
    end_x: number;
    end_y: number;
    duration?: number;
}

interface AppLaunchParams {
    package_name: string;
}

interface UIPageResultData {
    message: string;
    data: string;
}

interface ScreenshotResultData {
    message: string;
    data: {
        file_path: string;
        image_link: string;
        raw_result: any;
    };
}

interface ToolResultMap {
    'get_page_info': UIPageResultData;
    'get_page_screenshot_image': ScreenshotResultData;
    'app_launch': any;
    'tap': any;
    'double_tap': any;
    'long_press': any;
    'click_element': any;
    'set_input_text': any;
    'press_key': any;
    'swipe': any;
    'run_ui_subagent': any;
}

const UIAutomationTools = (function () {
    async function get_page_info(params: ToolParams): Promise<ToolResponse> {
        // This would be connected to the actual UI automation backend
        // For now returning a mock response
        const result = 'Mock UI hierarchy data';
        return { success: true, message: 'Successfully retrieved page info', data: result };
    }

    async function get_page_screenshot_image(params: {}): Promise<ToolResponse> {
        // This would capture actual screenshot
        // For now returning a mock response
        return {
            success: true,
            message: 'Screenshot captured',
            data: {
                file_path: '/sdcard/screenshot.png',
                image_link: 'screenshot://latest',
                raw_result: null,
            },
        };
    }

    async function tap(params: TapParams): Promise<ToolResponse> {
        // This would execute actual tap
        return { success: true, message: `Tapped at (${params.x}, ${params.y})`, data: null };
    }

    async function double_tap(params: DoubleTapParams): Promise<ToolResponse> {
        return { success: true, message: `Double tapped at (${params.x}, ${params.y})`, data: null };
    }

    async function long_press(params: LongPressParams): Promise<ToolResponse> {
        return { success: true, message: `Long pressed at (${params.x}, ${params.y})`, data: null };
    }

    async function click_element(params: ClickElementParams): Promise<ToolResponse> {
        return { success: true, message: 'Clicked element', data: params };
    }

    async function set_input_text(params: SetInputTextParams): Promise<ToolResponse> {
        return { success: true, message: `Input text: ${params.text}`, data: null };
    }

    async function press_key(params: PressKeyParams): Promise<ToolResponse> {
        return { success: true, message: `Pressed key: ${params.key_code}`, data: null };
    }

    async function swipe(params: SwipeParams): Promise<ToolResponse> {
        return {
            success: true,
            message: `Swiped from (${params.start_x}, ${params.start_y}) to (${params.end_x}, ${params.end_y})`,
            data: null,
        };
    }

    async function app_launch(params: AppLaunchParams): Promise<ToolResponse> {
        return { success: true, message: `Launched app: ${params.package_name}`, data: null };
    }

    async function wrapToolExecution<T extends keyof ToolResultMap>(
        func: (params: any) => Promise<ToolResponse>,
        params: any
    ): Promise<ToolResponse> {
        try {
            const result = await func(params);
            complete(result);
        } catch (error) {
            console.error(`Tool execution failed unexpectedly`, error);
            complete({
                success: false,
                message: `Tool execution failed: ${error instanceof Error ? error.message : String(error)}`,
            });
        }
    }

    async function main() {
        console.log('=== UI Automation Tools Test Started ===\n');
        const results = [];

        try {
            // 1. Test get_page_info
            console.log('1. Testing get_page_info...');
            const pageInfoResult = await get_page_info({});
            results.push({ tool: 'get_page_info', result: pageInfoResult });
            console.log('✓ get_page_info test completed\n');

            // 2. Test tap
            console.log('2. Testing tap...');
            const tapResult = await tap({ x: 500, y: 1000 });
            results.push({ tool: 'tap', result: tapResult });
            console.log('✓ tap test completed\n');

            // 3. Test press_key
            console.log('3. Testing press_key...');
            const pressKeyResult = await press_key({ key_code: 'KEYCODE_VOLUME_UP' });
            results.push({ tool: 'press_key', result: pressKeyResult });
            console.log('✓ press_key test completed\n');

            // 4. Test set_input_text
            console.log('4. Testing set_input_text...');
            const setTextResult = await set_input_text({ text: 'UI automation test text' });
            results.push({ tool: 'set_input_text', result: setTextResult });
            console.log('✓ set_input_text test completed\n');

            // 5. Test swipe
            console.log('5. Testing swipe...');
            const swipeResult = await swipe({
                start_x: 500,
                start_y: 1500,
                end_x: 500,
                end_y: 500,
                duration: 300,
            });
            results.push({ tool: 'swipe', result: swipeResult });
            console.log('✓ swipe test completed\n');

            // 6. Test click_element
            console.log('6. Testing click_element...');
            try {
                const clickResult = await click_element({
                    className: 'android.widget.Button',
                    index: 0,
                });
                results.push({ tool: 'click_element', result: clickResult });
                console.log('✓ click_element test completed\n');
            } catch (error) {
                console.log(
                    '⚠ click_element test failed (this may be normal if there are no buttons on the current page):',
                    (error as Error).message,
                    '\n'
                );
                results.push({
                    tool: 'click_element',
                    result: { success: false, message: (error as Error).message },
                });
            }

            console.log('=== UI Automation Tools Test Completed ===\n');
            console.log('Test Results Summary:');
            results.forEach((r, i) => {
                const status = r.result.success ? '✓' : '✗';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });
            complete({
                success: true,
                message: 'All UI tools tests completed',
                data: results,
            });
        } catch (error) {
            console.error('Error during testing:', error);
            complete({
                success: false,
                message: `Test failed: ${error instanceof Error ? error.message : String(error)}`,
                data: results,
            });
        }
    }

    return {
        get_page_info: (params: ToolParams) => wrapToolExecution(get_page_info, params),
        app_launch: (params: AppLaunchParams) => wrapToolExecution(app_launch, params),
        get_page_screenshot_image: () => wrapToolExecution(get_page_screenshot_image, {}),
        tap: (params: TapParams) => wrapToolExecution(tap, params),
        double_tap: (params: DoubleTapParams) => wrapToolExecution(double_tap, params),
        long_press: (params: LongPressParams) => wrapToolExecution(long_press, params),
        click_element: (params: ClickElementParams) => wrapToolExecution(click_element, params),
        set_input_text: (params: SetInputTextParams) => wrapToolExecution(set_input_text, params),
        press_key: (params: PressKeyParams) => wrapToolExecution(press_key, params),
        swipe: (params: SwipeParams) => wrapToolExecution(swipe, params),
        main,
    };
})();

export default UIAutomationTools;
export const get_page_info = UIAutomationTools.get_page_info;
export const app_launch = UIAutomationTools.app_launch;
export const get_page_screenshot_image = UIAutomationTools.get_page_screenshot_image;
export const tap = UIAutomationTools.tap;
export const double_tap = UIAutomationTools.double_tap;
export const long_press = UIAutomationTools.long_press;
export const click_element = UIAutomationTools.click_element;
export const set_input_text = UIAutomationTools.set_input_text;
export const press_key = UIAutomationTools.press_key;
export const swipe = UIAutomationTools.swipe;
export const run_ui_subagent = UIAutomationTools.main;