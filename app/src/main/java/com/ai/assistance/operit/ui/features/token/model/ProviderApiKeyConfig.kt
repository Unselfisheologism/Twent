package com.ai.assistance.operit.ui.features.token.model

import com.ai.assistance.operit.data.model.ApiProviderType

/**
 * Maps each API provider to its API key website URL.
 * Some providers may not have a specific API key page (e.g., local providers).
 */
object ProviderApiKeyConfig {
    
    data class ProviderApiKeyInfo(
        val providerType: ApiProviderType,
        val displayName: String,
        val apiKeyUrl: String,
        val signInUrl: String = ""
    )
    
    /**
     * Get the API key URL for a specific provider type.
     * @param providerType The provider type
     * @return The API key URL for the provider, or an empty string if not available
     */
    fun getApiKeyUrl(providerType: ApiProviderType): String {
        return getProviderInfo(providerType)?.apiKeyUrl ?: ""
    }
    
    /**
     * Get the provider info for a specific provider type.
     * @param providerType The provider type
     * @return The provider info, or null if not found
     */
    fun getProviderInfo(providerType: ApiProviderType): ProviderApiKeyInfo? {
        return allProviders.find { it.providerType == providerType }
    }
    
    /**
     * Get all providers that have API key URLs (excluding local providers and generic providers).
     * @return List of provider info objects
     */
    fun getProvidersWithApiKeyPages(): List<ProviderApiKeyInfo> {
        return allProviders.filter { it.apiKeyUrl.isNotEmpty() }
    }
    
    /**
     * All providers with their API key URLs.
     */
    val allProviders = listOf(
        // OpenAI
        ProviderApiKeyInfo(
            providerType = ApiProviderType.OPENAI,
            displayName = "OpenAI",
            apiKeyUrl = "https://platform.openai.com/api-keys",
            signInUrl = "https://platform.openai.com/login"
        ),
        ProviderApiKeyInfo(
            providerType = ApiProviderType.OPENAI_RESPONSES,
            displayName = "OpenAI (Responses API)",
            apiKeyUrl = "https://platform.openai.com/api-keys",
            signInUrl = "https://platform.openai.com/login"
        ),
        ProviderApiKeyInfo(
            providerType = ApiProviderType.OPENAI_GENERIC,
            displayName = "OpenAI Compatible",
            apiKeyUrl = "https://platform.openai.com/api-keys",
            signInUrl = "https://platform.openai.com/login"
        ),
        
        // Anthropic/Claude
        ProviderApiKeyInfo(
            providerType = ApiProviderType.ANTHROPIC,
            displayName = "Anthropic (Claude)",
            apiKeyUrl = "https://console.anthropic.com/settings/keys",
            signInUrl = "https://console.anthropic.com/login"
        ),
        ProviderApiKeyInfo(
            providerType = ApiProviderType.ANTHROPIC_GENERIC,
            displayName = "Anthropic Compatible",
            apiKeyUrl = "https://console.anthropic.com/settings/keys",
            signInUrl = "https://console.anthropic.com/login"
        ),
        
        // Google/Gemini
        ProviderApiKeyInfo(
            providerType = ApiProviderType.GOOGLE,
            displayName = "Google (Gemini)",
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            signInUrl = "https://accounts.google.com/signin"
        ),
        ProviderApiKeyInfo(
            providerType = ApiProviderType.GEMINI_GENERIC,
            displayName = "Gemini Compatible",
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            signInUrl = "https://accounts.google.com/signin"
        ),
        
        // DeepSeek
        ProviderApiKeyInfo(
            providerType = ApiProviderType.DEEPSEEK,
            displayName = "DeepSeek",
            apiKeyUrl = "https://platform.deepseek.com/api_keys",
            signInUrl = "https://platform.deepseek.com/sign_in"
        ),
        
        // Baidu (Wenxin)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.BAIDU,
            displayName = "Baidu (Wenxin)",
            apiKeyUrl = "https://console.bce.baidu.com/qianfan/ais/console/onlineService",
            signInUrl = "https://login.bce.baidu.com/"
        ),
        
        // Aliyun (Tongyi Qianwen)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.ALIYUN,
            displayName = "Aliyun (Tongyi Qianwen)",
            apiKeyUrl = "https://dashscope.console.aliyun.com/apiKey",
            signInUrl = "https://account.alibaba.com/login.htm"
        ),
        
        // Xunfei (Spark)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.XUNFEI,
            displayName = "Xunfei (Spark)",
            apiKeyUrl = "https://console.xfyun.cn/services/bm4",
            signInUrl = "https://www.xfyun.cn/login"
        ),
        
        // Zhipu (ChatGLM)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.ZHIPU,
            displayName = "Zhipu (ChatGLM)",
            apiKeyUrl = "https://open.bigmodel.cn/usercenter/apikeys",
            signInUrl = "https://open.bigmodel.cn/login"
        ),
        
        // Baichuan
        ProviderApiKeyInfo(
            providerType = ApiProviderType.BAICHUAN,
            displayName = "Baichuan",
            apiKeyUrl = "https://platform.baichuan-ai.com/console/apikey",
            signInUrl = "https://platform.baichuan-ai.com/login"
        ),
        
        // Moonshot (Kimi)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.MOONSHOT,
            displayName = "Moonshot (Kimi)",
            apiKeyUrl = "https://platform.moonshot.cn/console/api-keys",
            signInUrl = "https://platform.moonshot.cn/login"
        ),
        
        // Mistral
        ProviderApiKeyInfo(
            providerType = ApiProviderType.MISTRAL,
            displayName = "Mistral AI",
            apiKeyUrl = "https://console.mistral.ai/api-keys/",
            signInUrl = "https://console.mistral.ai/login"
        ),
        
        // SiliconFlow
        ProviderApiKeyInfo(
            providerType = ApiProviderType.SILICONFLOW,
            displayName = "SiliconFlow",
            apiKeyUrl = "https://cloud.siliconflow.cn/account/ak",
            signInUrl = "https://cloud.siliconflow.cn/login"
        ),
        
        // OpenRouter
        ProviderApiKeyInfo(
            providerType = ApiProviderType.OPENROUTER,
            displayName = "OpenRouter",
            apiKeyUrl = "https://openrouter.ai/settings/keys",
            signInUrl = "https://openrouter.ai/login"
        ),
        
        // InfiniAI
        ProviderApiKeyInfo(
            providerType = ApiProviderType.INFINIAI,
            displayName = "InfiniAI",
            apiKeyUrl = "https://cloud.infini-ai.com/maas/console/apikey",
            signInUrl = "https://cloud.infini-ai.com/login"
        ),
        
        // Alipay Bailing
        ProviderApiKeyInfo(
            providerType = ApiProviderType.ALIPAY_BAILING,
            displayName = "Alipay Bailing",
            apiKeyUrl = "https://tbox.cn/",
            signInUrl = "https://tbox.cn/login"
        ),
        
        // Doubao (Huoshan Model)
        ProviderApiKeyInfo(
            providerType = ApiProviderType.DOUBAO,
            displayName = "Doubao (Huoshan)",
            apiKeyUrl = "https://console.volcengine.com/ark/region/ark/openapi/apikey",
            signInUrl = "https://www.volcengine.com/login"
        ),
        
        // PPInfra
        ProviderApiKeyInfo(
            providerType = ApiProviderType.PPINFRA,
            displayName = "PPInfra",
            apiKeyUrl = "https://www.ppinfra.com/settings/key",
            signInUrl = "https://www.ppinfra.com/login"
        ),
        
        // Kilo Gateway
        ProviderApiKeyInfo(
            providerType = ApiProviderType.KILO_GATEWAY,
            displayName = "Kilo AI Gateway",
            apiKeyUrl = "https://kilo.ai/dashboard",
            signInUrl = "https://kilo.ai/login"
        ),
        
        // NVIDIA NIMS Gateway
        ProviderApiKeyInfo(
            providerType = ApiProviderType.NVIDIA_NIMS_GATEWAY,
            displayName = "NVIDIA NIMS",
            apiKeyUrl = "https://build.nvidia.com/explore/discover",
            signInUrl = "https://build.nvidia.com/login"
        )
        
        // Note: LMSTUDIO, MNN, LLAMA_CPP, and OTHER are not included
        // as they are local providers or generic providers without specific API key pages
    )
}