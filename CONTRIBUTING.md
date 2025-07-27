# Contributing to Child Screen Time

Thank you for your interest in contributing to Child Screen Time! This open source parental control application benefits from community contributions, and we welcome developers, testers, and users who want to help improve digital wellness for families.

## 🌟 How to Contribute

### Types of Contributions We Welcome

- **Bug Reports**: Help us identify and fix issues
- **Feature Requests**: Suggest new functionality
- **Code Contributions**: Implement new features or bug fixes
- **Documentation**: Improve README, code comments, or wiki
- **Testing**: Test the app on different devices and Android versions
- **UI/UX Improvements**: Enhance the user experience
- **Translations**: Help make the app accessible in more languages

## 🚀 Getting Started

### Prerequisites

Before contributing, ensure you have:

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **Android SDK**: API level 28 (Android 9.0) or higher
- **Git**: For version control
- **Java/Kotlin Knowledge**: Basic understanding of Android development
- **Device/Emulator**: For testing your changes

### Development Environment Setup

1. **Fork the Repository**
   ```bash
   # Fork the repo on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/childscreentime.git
   cd childscreentime
   ```

2. **Set Up Upstream Remote**
   ```bash
   git remote add upstream https://github.com/childscreentime/childscreentime.git
   ```

3. **Open in Android Studio**
   - Import the project
   - Let Gradle sync complete
   - Ensure no build errors

4. **Test the Build**
   ```bash
   ./gradlew clean assembleDebug
   ```

## 🐛 Reporting Bugs

When reporting bugs, please include:

### Bug Report Template

```markdown
**Describe the Bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected Behavior**
What you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Device Information:**
- Device: [e.g. Samsung Galaxy S21]
- OS Version: [e.g. Android 12]
- App Version: [e.g. 2.0]

**Logcat Output**
```
Add relevant logcat output here
```

**Additional Context**
Add any other context about the problem here.
```

### Where to Report
- **GitHub Issues**: [Create an issue](https://github.com/childscreentime/childscreentime/issues/new)
- **Include logs**: Use `adb logcat | grep -E "(TimeManager|ScreenLockService|ScreenTimeWorker)"`

## 💡 Suggesting Features

We love feature suggestions! Before submitting:

1. **Check existing issues** to avoid duplicates
2. **Consider the scope** - features should align with parental control goals
3. **Think about privacy** - maintain our zero data collection commitment
4. **Consider complexity** - simpler features are more likely to be implemented

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of what the problem is.

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Other solutions or features you've considered.

**Additional context**
- Mockups or sketches
- Similar features in other apps
- Technical considerations
```

## 🔧 Code Contributions

### Before You Start

1. **Check existing issues** for planned work
2. **Create an issue** to discuss larger changes
3. **Follow our coding standards** (detailed below)
4. **Write tests** for new functionality

### Development Workflow

1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Your Changes**
   - Follow the coding standards
   - Write clear commit messages
   - Test your changes thoroughly

3. **Update Documentation**
   - Update README if needed
   - Add code comments
   - Update relevant documentation

4. **Run Tests**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

5. **Push and Create Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

### Pull Request Guidelines

#### PR Title Format
- `feat: add time scheduling feature`
- `fix: resolve service restart issue`
- `docs: update installation instructions`
- `test: add unit tests for TimeManager`

#### PR Description Template
```markdown
## Description
Brief description of changes and motivation.

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed
- [ ] Tested on multiple devices/Android versions

## Screenshots/Videos
If applicable, add screenshots or videos demonstrating the changes.

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Code is commented, particularly in hard-to-understand areas
- [ ] Documentation updated
- [ ] No breaking changes (or clearly documented)
```

## 📋 Coding Standards

### Java Style Guide

#### General Principles
- **Clarity over cleverness**: Write readable, maintainable code
- **Follow Android conventions**: Use standard Android patterns
- **Null safety**: Always check for null values
- **Error handling**: Implement proper exception handling

#### Code Formatting
```java
// Class names: PascalCase
public class TimeManager {
    
    // Constants: UPPER_SNAKE_CASE
    private static final String TAG = "TimeManager";
    private static final long DEFAULT_INTERVAL = 30000;
    
    // Methods: camelCase
    public static boolean updateBlockedState(Context context) {
        // Local variables: camelCase
        boolean wasBlocked = app.blocked;
        
        // Clear, descriptive variable names
        long currentUsageMinutes = Utils.millisToMinutes(duration);
        
        // Proper error handling
        try {
            // Implementation
        } catch (Exception e) {
            Log.e(TAG, "Error updating blocked state", e);
            return false;
        }
    }
}
```

#### Documentation Standards
```java
/**
 * Updates the blocking state based on current usage and available credit.
 * 
 * This method performs the core logic for determining whether the device
 * should be blocked based on time usage. It updates the global application
 * state and notifies relevant services.
 * 
 * @param context The application context for accessing system services
 * @return true if the blocking state changed, false otherwise
 * @throws SecurityException if usage access permission is not granted
 */
public static boolean updateBlockedState(Context context) {
    // Implementation
}
```

#### Logging Guidelines
```java
// Use appropriate log levels
Log.d(TAG, "Debug information for development");
Log.i(TAG, "General information about app state");
Log.w(TAG, "Warning about potential issues");
Log.e(TAG, "Error that needs attention", exception);

// Include context in log messages
Log.d(TAG, "Usage check: " + minutes + " minutes used, " + credit + " minutes allowed");
```

### XML Guidelines

#### Layout Files
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- Use descriptive IDs -->
    <TextView
        android:id="@+id/usage_display_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/current_usage_label"
        android:textSize="18sp" />

</LinearLayout>
```

#### String Resources
```xml
<resources>
    <!-- Group related strings -->
    <!-- Time display strings -->
    <string name="current_usage_label">Current Usage</string>
    <string name="time_remaining_label">Time Remaining</string>
    
    <!-- Error messages -->
    <string name="permission_denied_message">Usage access permission required</string>
</resources>
```

## 🧪 Testing Guidelines

### Unit Tests
- **Test public methods** in isolation
- **Mock dependencies** using Mockito
- **Test edge cases** and error conditions
- **Maintain high coverage** for critical components

```java
@Test
public void testUpdateBlockedState_WithValidCredit_UpdatesCorrectly() {
    // Arrange
    Context mockContext = mock(Context.class);
    // Setup mocks
    
    // Act
    boolean result = TimeManager.updateBlockedState(mockContext);
    
    // Assert
    assertTrue(result);
    // Verify expected behavior
}
```

### Integration Tests
- **Test complete workflows** end-to-end
- **Use real Android components** where possible
- **Test permission flows** and system interactions

### Manual Testing Checklist
Before submitting a PR, test:

- [ ] **Fresh installation** on clean device
- [ ] **Permission granting** flow works correctly
- [ ] **Time tracking** accuracy over extended period
- [ ] **Blocking enforcement** cannot be bypassed
- [ ] **Service survival** after system optimization
- [ ] **Battery impact** is minimal
- [ ] **Different Android versions** (API 28+)
- [ ] **Different device types** (phones, tablets)

## 🏗️ Architecture Guidelines

### Key Principles
- **Separation of concerns**: Each class has a single responsibility
- **Dependency injection**: Use constructor injection where possible
- **Immutable data**: Prefer immutable objects for data transfer
- **Reactive patterns**: Use observers for state changes

### Component Guidelines

#### Services
```java
public class ScreenLockService extends Service {
    // Services should be focused on specific tasks
    // Use foreground services for long-running operations
    // Implement proper lifecycle management
}
```

#### Activities
```java
public class MainActivity extends AppCompatActivity {
    // Activities should be lightweight
    // Delegate business logic to separate classes
    // Handle configuration changes properly
}
```

#### Data Classes
```java
public class Credit {
    // Immutable data objects when possible
    // Clear, descriptive field names
    // Proper equals/hashCode implementation
}
```

## 🌍 Internationalization

### Adding Translations
1. **Create language directories**: `res/values-{language}/`
2. **Translate string resources**: All user-facing text
3. **Test layout**: Ensure UI works with longer text
4. **Cultural considerations**: Adapt for local contexts

### Translation Guidelines
- **Keep context in mind**: Provide context for translators
- **Use placeholders**: For dynamic content
- **Test thoroughly**: Different languages have different text lengths

## 🔒 Security Considerations

### Code Security
- **Input validation**: Always validate external input
- **Permission checks**: Verify permissions before sensitive operations
- **Secure defaults**: Fail securely when errors occur
- **No hardcoded secrets**: Use secure storage for sensitive data

### Privacy Guidelines
- **No data collection**: Maintain zero data collection policy
- **Local processing**: Keep all operations on-device
- **Clear permissions**: Document why each permission is needed

## 📝 Documentation Standards

### Code Comments
```java
// Single-line comments for brief explanations
/* Multi-line comments for detailed explanations
 * that require more space and formatting */

/**
 * JavaDoc for public APIs - always include:
 * - Purpose and behavior
 * - Parameter descriptions
 * - Return value description
 * - Exception conditions
 * - Usage examples if complex
 */
```

### README Updates
- **Keep current**: Update documentation with code changes
- **Clear examples**: Provide working code examples
- **Screenshots**: Update images when UI changes
- **Version compatibility**: Note Android version requirements

## 🚀 Release Process

### Version Numbers
Follow semantic versioning (MAJOR.MINOR.PATCH):
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Release Checklist
- [ ] All tests pass
- [ ] Documentation updated
- [ ] Version number incremented
- [ ] Release notes prepared
- [ ] APK tested on multiple devices

## 🤝 Community Guidelines

### Code of Conduct
- **Be respectful**: Treat all contributors with respect
- **Be inclusive**: Welcome contributors from all backgrounds
- **Be constructive**: Provide helpful feedback
- **Be patient**: Remember that everyone is learning

### Communication
- **GitHub Issues**: For bug reports and feature requests
- **Pull Requests**: For code discussions
- **Discussions**: For general questions and ideas

### Recognition
Contributors will be recognized in:
- **README**: Contributors section
- **Release notes**: Acknowledgment of contributions
- **Git history**: Proper attribution in commits

## ❓ Getting Help

### Where to Ask Questions
- **GitHub Discussions**: General questions and brainstorming
- **Issues**: Specific bugs or feature requests
- **Code Comments**: Implementation-specific questions

### Debugging Tips
```bash
# View app logs
adb logcat | grep "io.github.childscreentime"

# Check service status
adb shell dumpsys activity services io.github.childscreentime

# Monitor permissions
adb shell dumpsys package io.github.childscreentime
```

## 📈 Performance Guidelines

### Memory Management
- **Avoid memory leaks**: Properly cleanup resources
- **Use weak references**: For callbacks and listeners
- **Optimize images**: Compress resources appropriately

### Battery Optimization
- **Efficient scheduling**: Use appropriate intervals
- **Reduce wake locks**: Minimize device wake time
- **Background limits**: Respect Android's background restrictions

---

**Thank you for contributing to Child Screen Time! Together, we can help families build healthier relationships with technology.**

For questions about contributing, please create an issue or start a discussion on GitHub.
