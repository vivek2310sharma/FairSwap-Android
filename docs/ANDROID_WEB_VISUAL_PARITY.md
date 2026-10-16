# FairSwap Android — Visual Parity Mapping

## Brand Palette
| Web Origin | Web Token | Web Value | Android Token | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| `globals.css` | `--color-brand-50` | `#ecfdf5` | `Brand50` | Source-matched |
| `globals.css` | `--color-brand-100` | `#d1fae5` | `Brand100` | Source-matched |
| `globals.css` | `--color-brand-500` | `#059669` | `Brand500` | Source-matched |
| `globals.css` | `--color-brand-600` | `#047857` | `Brand600` | Source-matched |
| `globals.css` | `--color-brand-700` | `#065f46` | `Brand700` | Source-matched |
| `globals.css` | `--color-brand-900` | `#022c22` | `Brand900` | Source-matched |

## Semantic Colors (Light/Dark)
| Web Origin | Web Token | Web Value | Android Token | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| `globals.css` | `--color-background` | `#fafafa` / `#171717` | `background` (FairSwapWebTheme) | Source-matched |
| `globals.css` | `--color-foreground` | `#171717` / `#fafafa` | `onBackground` / `onSurface` | Source-matched |
| `globals.css` | `--color-muted` | `#525252` / `#a3a3a3` | `onSurfaceVariant` | Source-matched |
| `globals.css` | `--color-border` | `#e5e5e5` / `#404040` | `outlineVariant` | Source-matched |
| `globals.css` | `--color-surface` | `#f5f5f5` / `#262626` | `surface` | Source-matched |
| `globals.css` | `--color-card` | `#ffffff` / `#262626` | `surfaceContainer` | Source-matched |
| `globals.css` | `--color-notice-bg` | `#fef3c7` / `#451a03` | `noticeBg` (LocalExtendedColors) | Source-matched |
| `globals.css` | `--color-notice-border` | `#f59e0b` / `#b45309` | `noticeBorder` (LocalExtendedColors) | Source-matched |
| `globals.css` | `--color-notice-text` | `#92400e` / `#fde68a` | `noticeText` (LocalExtendedColors) | Source-matched |
| Unspecified (Web) | N/A | N/A | `errorBg`/`errorBorder`/`errorText` mapped from Web `red-50/200/700` | Native-adaptation (No Web equivalent var) |
| Unspecified (Web) | N/A | N/A | `successBg`/`successBorder`/`successText` mapped from Web `emerald-50/200/700` | Native-adaptation (No Web equivalent var) |

## Typography
| Web Origin | Web Stack | Android Adaptation | Verification Status |
| :--- | :--- | :--- | :--- |
| `globals.css` | `ui-sans-serif, system-ui, Roboto, ...` | `Roboto` (Default Android Sans) | Native-adaptation |
| `header.tsx` | Wordmark `.text-xl.font-bold.tracking-tight` | `MaterialTheme.typography.titleLarge` | Native-adaptation (Approx font size/weight mapping) |

## Icon
| Web Origin | Visual Treatment | Android Adaptation | Verification Status |
| :--- | :--- | :--- | :--- |
| `icon.svg` | White `FS` text on `#059669` tile, `rx=6` | VectorDrawable foreground layer over solid color layer, respecting adaptive icon safe area. Text rendered to paths. Includes monochrome layer | Native-adaptation |
| `N/A` | `Close` Icon | `ImageVector` with explicit `path` data from Compose Material Icons | Native-adaptation |

## Components
| Web Component | Web Treatment | Android Adaptation | Verification Status |
| :--- | :--- | :--- | :--- |
| `Primary Button` | `bg-brand-600`, `text-white`, `rounded-md`/`lg`, `font-semibold` | `Button` with custom colors & shape. Min height `48dp` for accessibility. Spinner space reserved. | Native-adaptation (Min interactive bounds enforced) |
| `Text Field` | `rounded-md`, `border-gray-300`, `focus:border-emerald-500` | `OutlinedTextField` using `Gray300` and `Emerald500`. External label semantics applied. | Source-matched |
| `Card` | `rounded-xl`, `border-border`, `bg-card`/`bg-white`, `shadow-sm` | `Card` using `surfaceContainer` | Source-matched |
| `Notice/Alert` | `rounded-lg border bg-amber-50 p-3` (and Error/Success variants) | Custom `FairSwapNotice` matching layout via `LocalExtendedColors` matching Web form specific utility colors (`Amber`, `Emerald`, `Red`). | Native-adaptation |
| `Badge` | `bg-brand-100`, `text-brand-800`, `rounded-full` | `FairSwapBadge` | Native-adaptation |
| `Chip/Tab` | `border`, `bg-white`, selection states | `FairSwapChip` | Native-adaptation |
| `Divider` | `border-border` | `FairSwapDivider` using `outlineVariant` | Source-matched |

## Remaining Rollout
- Verify design components in preview tooling. Included forced inverse themes to validate context scoping.
- Reflows via `FlowRow` used for narrow screens and fontScale 2.0 to ensure buttons and fields wrap effectively.
- Roll out `FairSwapWebTheme` explicitly to the remainder of the app's components (Discovery, Messages, etc) once the pilot AccountScreen has been approved.
