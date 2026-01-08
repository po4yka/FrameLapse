---
name: nav3-viewmodel
description: Integrates ViewModels with Navigation 3 including argument injection via Hilt AssistedInject or Koin. Use when adding ViewModels to Nav3 screens, passing navigation arguments to ViewModels, or setting up DI for navigation.
---

# ViewModel Integration with Nav3

## Overview

This skill helps integrate ViewModels with Navigation 3 screens, including passing navigation arguments to ViewModels using Hilt or Koin.

## Option 1: Hilt with AssistedInject

### Define the ViewModel

```kotlin
@HiltViewModel(assistedFactory = ProductViewModel.Factory::class)
class ProductViewModel @AssistedInject constructor(
    @Assisted private val productId: String,
    private val repository: ProductRepository
) : ViewModel() {

    // ViewModel logic

    @AssistedFactory
    interface Factory {
        fun create(productId: String): ProductViewModel
    }
}
```

### Use in NavEntry

```kotlin
entry<ProductDetail> { key ->
    val viewModel = hiltViewModel<ProductViewModel, ProductViewModel.Factory> { factory ->
        factory.create(key.productId)
    }
    ProductScreen(viewModel)
}
```

## Option 2: Koin

### Define the ViewModel

```kotlin
class ProductViewModel(
    private val productId: String,
    private val repository: ProductRepository
) : ViewModel() {
    // ViewModel logic
}
```

### Register in Koin Module

```kotlin
val viewModelModule = module {
    viewModel { params ->
        ProductViewModel(
            productId = params.get(),
            repository = get()
        )
    }
}
```

### Use in NavEntry

```kotlin
entry<ProductDetail> { key ->
    val viewModel = koinViewModel<ProductViewModel> { parametersOf(key.productId) }
    ProductScreen(viewModel)
}
```

## Option 3: Manual Factory (No DI)

```kotlin
entry<ProductDetail> { key ->
    val viewModel = viewModel<ProductViewModel>(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProductViewModel(key.productId) as T
            }
        }
    )
    ProductScreen(viewModel)
}
```

## ViewModel Scoping with NavEntry Decorator

Ensure ViewModel is scoped to the NavEntry lifecycle:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberViewModelStoreNavEntryDecorator()
    ),
    entryProvider = entryProvider { ... }
)
```

## Reference Examples

- Hilt ViewModel: See `app/src/main/java/com/example/nav3recipes/passingarguments/hilt/`
- Koin ViewModel: See `app/src/main/java/com/example/nav3recipes/passingarguments/koin/`
- Basic ViewModel: See `app/src/main/java/com/example/nav3recipes/passingarguments/basic/`

## Checklist

- [ ] ViewModel defined with appropriate DI annotations
- [ ] Factory/module configured for argument injection
- [ ] NavEntry uses correct ViewModel creation method
- [ ] ViewModelStoreNavEntryDecorator added if needed
