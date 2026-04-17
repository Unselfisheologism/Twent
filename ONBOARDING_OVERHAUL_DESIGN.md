# Twent AI Onboarding Overhaul Design

## Design Philosophy
**Aesthetic:** Dithered, Pixel, Techy, Godly
**Positioning:** Personal Agentic OS & Personal Assistant in Your Pocket
**Psychology:** 3-phase Diagnostic-Prescription-Activation framework

## Color Palette
- **Primary:** #FFF09020 (Orange) - Divine/tech glow
- **Secondary:** #FF80C0F0 (Cyan) - Techy/digital
- **Background:** #FF0A0A0A (Deep black) - Godly void
- **Surface:** #FF141414 (Dark grey) - Dithered texture
- **Accent:** #FF405050 (Steel) - Techy accents
- **Text:** #FFFFFFFF (White), #FFB0B0B0 (Light grey)

## Typography
- **Headlines:** Oxanium (futuristic, techy)
- **Body:** Roboto (clean, readable)
- **Code/Terminal:** Monospace for CLI elements

## Visual Elements
1. **Dithering Effect:** Gradient noise patterns in backgrounds
2. **Pixel Art Icons:** 8-bit style icons for tools/features
3. **Godly Glow:** Radial gradients with divine light effects
4. **Grid Patterns:** Subtle grid lines for techy feel
5. **Animated Particles:** Floating digital particles

---

## Onboarding Flow (5 Screens)

### Screen 1: The Diagnostic Introduction
**Phase:** Diagnosis (Identity Stake + Emotional Hook)
**Purpose:** Establish user identity and validate frustration

**Visuals:**
- Dark void background with subtle dithering
- Animated pixelated "eye" opening (SVG animation)
- Divine light rays emanating from center

**Copy:**
- Headline: "Your Phone is Underutilized"
- Subheadline: "You're using 10% of what's possible"
- Question: "What kind of user are you?"
- Options (pixelated buttons):
  1. "Power User - I want automation"
  2. "Developer - I want CLI access" 
  3. "Creator - I want AI assistance"
  4. "Explorer - Show me everything"

**Psychology:** Forces user to categorize themselves, creating investment

### Screen 2: The Agentic OS Revelation
**Phase:** Value Climax (Feature Integration)
**Purpose:** Show the core value proposition

**Visuals:**
- Animated SVG showing phone transforming into an "OS"
- Particle effects connecting different capabilities
- Dithered gradient background with tech patterns

**Copy:**
- Headline: "Your Personal Agentic OS"
- Subheadline: "All in your pocket"
- Features shown with pixel icons:
  - 🤖 AI Agent with 40+ tools
  - 🖥️ Full Ubuntu 24 Terminal
  - 🔌 1000+ App Connections
  - ⚡ Workflow Automations
  - 🧠 Skills & MCP Servers

**Animation:** Each feature appears with a "glitch" effect

### Screen 3: The Agent CLI Showcase
**Phase:** Value Climax (Personalized Pathing)
**Purpose:** Show advanced capabilities for power users

**Visuals:**
- Split screen: Left shows terminal, right shows phone UI
- Animated typing effect in terminal
- Pixel art representations of Claude Code, Codex, Hermes-Agent

**Copy:**
- Headline: "World's Best Agent CLIs"
- Subheadline: "In your pocket"
- Features:
  - Claude Code - Build anything
  - Codex - Autonomous coding
  - Hermes-Agent - Your AI companion
  - Full Linux environment

**Animation:** Terminal typing animation with command execution

### Screen 4: The Overlay Agent Power
**Phase:** Value Climax (Commitment Ritual)
**Purpose:** Show the ultimate power of UI automation

**Visuals:**
- Phone outline with overlay agent activated
- Animated taps, swipes, scrolls on screen
- Dithered "god mode" effect

**Copy:**
- Headline: "Ultimate UI Automation"
- Subheadline: "Your Agent sees and controls everything"
- Features:
  - Long-press power button to activate
  - Voice activation anywhere
  - Screen context capture
  - Tap, swipe, type automation
  - Works with any app

**Animation:** Phone screen showing automated interactions

### Screen 5: The Hard Conversion
**Phase:** Activation (Permission Priming + Immediate Action)
**Purpose:** Get permissions and start using the app

**Visuals:**
- Clean, focused interface
- Pixel art "activate" button
- Progress indicators

**Copy:**
- Headline: "Activate Your Agentic OS"
- Subheadline: "Grant permissions to unlock full power"
- Permissions needed (with explanations):
  1. Accessibility - For UI automation
  2. Overlay - For always-available agent
  3. Terminal - For CLI access
- Button: "Activate Now"
- Skip option: "Explore in Basic Mode"

**Psychology:** User has invested time, now asked for reasonable permissions

---

## Special Modes

### Basic Mode (for non-technical users)
- Simplified interface
- Pre-built workflows
- No terminal access
- Guided tutorials

### Power User Mode
- Full terminal access
- All tools enabled
- Custom workflows
- Advanced settings

---

## SVG Animation Specifications

### 1. Dithering Background
```svg
<filter id="dither">
  <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="3" result="noise"/>
  <feDisplacementMap in="SourceGraphic" in2="noise" scale="2" xChannelSelector="R" yChannelSelector="G"/>
</filter>
```

### 2. Pixelated Eye Opening
- Path animation drawing eye outline
- Glitch effect on iris
- Divine light rays with gradient animation

### 3. Phone Transformation
- Morph animation from phone to "OS" icon
- Particle connections between features
- Glow effect on transformation

### 4. Terminal Typing
- Character-by-character animation
- Blinking cursor
- Command execution highlights

### 5. Overlay Activation
- Phone outline drawing
- Overlay circle expanding
- Automation taps appearing

---

## Implementation Plan

### Phase 1: Create New Onboarding Composable
- New `TwentOnboardingScreen.kt`
- 5-step horizontal pager
- Custom animations per screen
- State management for user choices

### Phase 2: SVG Animations
- Create SVG assets for each screen
- Implement with Compose Canvas or AndroidSVG
- Add particle systems

### Phase 3: Integration
- Replace existing OnboardingScreen
- Update navigation logic
- Add Basic/Power mode selection

### Phase 4: Testing & Refinement
- A/B test with current onboarding
- Measure activation rates
- Refine copy and animations

---

## Success Metrics
- Time to activation < 2 minutes
- Permission grant rate > 70%
- Day 1 retention > 40%
- User understanding of core value > 80%

## Notes
- Keep the "free app" notice but integrate it naturally
- Terms of service link remains subtle
- All animations respect `prefers-reduced-motion`
- Accessibility support maintained
