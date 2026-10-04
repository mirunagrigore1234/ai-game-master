import { useRef } from 'react';
import { View, Text, Pressable, StyleSheet, Animated } from 'react-native';
import { router } from 'expo-router';

export default function HomeScreen() {
  const buttonScale = useRef(new Animated.Value(1)).current;
  const pressIn = () => Animated.spring(buttonScale, { toValue: 0.97, useNativeDriver: true }).start();
  const pressOut = () => Animated.spring(buttonScale, { toValue: 1, useNativeDriver: true }).start();

  return (
    <View style={styles.container}>
      <Text style={styles.kicker}>THE ULTIMATE SOCIAL CHALLENGE</Text>
      <Text style={styles.title}>AI Game{'\n'}Master</Text>

      <Text style={styles.subtitle}>
        The AI runs the game. You play it.
      </Text>

      <Animated.View style={{ width: '100%', maxWidth: 350, transform: [{ scale: buttonScale }] }}>
      <Pressable
        style={[styles.button, styles.primaryButton]}
        onPressIn={pressIn}
        onPressOut={pressOut}
        onPress={() => router.push('/create')}
      >
        <Text style={styles.buttonText}>CREATE A GAME</Text>
      </Pressable>
      </Animated.View>

      <Pressable
        style={({ pressed }) => [styles.button, styles.secondaryButton, pressed && styles.pressed]}
        onPress={() => router.push('/join')}
      >
        <Text style={styles.secondaryButtonText}>JOIN A GAME</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    gap: 16,
    backgroundColor: '#10121B',
  },
  kicker: { color: '#9D8CFF', fontSize: 11, fontWeight: '800', letterSpacing: 2, marginBottom: 10 },
  title: {
    color: '#FFFFFF',
    fontSize: 44,
    fontWeight: '900',
    letterSpacing: -1,
    lineHeight: 46,
    textAlign: 'center',
  },
  subtitle: {
    color: '#A9ADBE',
    fontSize: 16,
    lineHeight: 23,
    textAlign: 'center',
    marginBottom: 24,
  },
  button: {
    alignItems: 'center',
    borderRadius: 16,
    minHeight: 58,
    justifyContent: 'center',
    padding: 16,
  },
  primaryButton: {
    backgroundColor: '#786BFF',
    shadowColor: '#786BFF',
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 5,
  },
  secondaryButton: {
    backgroundColor: '#1D2130',
    borderColor: '#373D54',
    borderWidth: 1,
    alignItems: 'center',
    width: '100%',
    maxWidth: 350,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '900',
    letterSpacing: 1,
  },
  secondaryButtonText: { color: '#D9DBE8', fontSize: 15, fontWeight: '800', letterSpacing: 1 },
  pressed: { opacity: 0.78 },
});