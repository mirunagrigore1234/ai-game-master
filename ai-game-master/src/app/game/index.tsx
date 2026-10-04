import { router, useLocalSearchParams } from 'expo-router';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as MediaLibrary from 'expo-media-library';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Image, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { API_BASE_URL } from '@/config/api';

type Challenge = {
  id: string;
  challenge: string;
  difficulty: string;
  points: number;
  category: string;
  state: 'pending' | 'completed' | 'skipped' | 'rejected';
};

type LeaderboardEntry = {
  playerId: string;
  name: string;
  score: number;
  completedChallengeCount: number;
  skippedChallengeCount: number;
  totalChallenges: number;
};

type GameState = {
  status?: 'playing' | 'finished';
  endTime?: string;
};

type Evidence = {
  evidenceId: string;
  challengeId: string;
  playerId: string;
  playerName: string;
  challenge: string;
  imageUrl: string;
  status: 'submitted' | 'analyzing' | 'analyzed';
  aiApproved: boolean | null;
  aiConfidence: number | null;
  aiAnalysis: string | null;
  createdAt: string;
  approveVotes: number;
  rejectVotes: number;
  submittedVoteCount: number;
  totalEligibleVoters: number;
  finalDecision: 'approved' | 'rejected' | null;
  pointsAwarded: number;
  currentVoterDecision: 'approve' | 'reject' | null;
};

export default function GameScreen() {
  const params = useLocalSearchParams<{
    code?: string | string[];
    playerId?: string | string[];
  }>();
  const code = Array.isArray(params.code) ? params.code[0] : params.code;
  const playerId = Array.isArray(params.playerId) ? params.playerId[0] : params.playerId;
  const [challenges, setChallenges] = useState<Challenge[]>([]);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [game, setGame] = useState<GameState>({});
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [cameraVisible, setCameraVisible] = useState(false);
  const [cameraFacing, setCameraFacing] = useState<'front' | 'back'>('back');
  const [capturedPhotoUri, setCapturedPhotoUri] = useState<string | null>(null);
  const [usedPhotoUris, setUsedPhotoUris] = useState<Record<string, string>>({});
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [uploadingEvidence, setUploadingEvidence] = useState(false);
  const cameraRef = useRef<CameraView>(null);
  const hasNavigatedToResults = useRef(false);
  const [cameraPermission, requestCameraPermission] = useCameraPermissions();

  useEffect(() => {
    if (!code || !playerId) {
      return;
    }

    let mounted = true;
    const load = async () => {
      try {
        const encodedCode = encodeURIComponent(code);
        const [gameResponse, challengeResponse, leaderboardResponse, evidenceResponse] = await Promise.all([
          fetch(`${API_BASE_URL}/games/${encodedCode}`),
          fetch(`${API_BASE_URL}/games/${encodedCode}/challenges?playerId=${encodeURIComponent(playerId)}`),
          fetch(`${API_BASE_URL}/games/${encodedCode}/leaderboard`),
          fetch(`${API_BASE_URL}/games/${encodedCode}/evidence?playerId=${encodeURIComponent(playerId)}`),
        ]);
        if (!gameResponse.ok || !challengeResponse.ok || !leaderboardResponse.ok || !evidenceResponse.ok) {
          return;
        }
        const latestGame: GameState = await gameResponse.json();
        const latestChallenges: Challenge[] = await challengeResponse.json();
        const latestLeaderboard: LeaderboardEntry[] = await leaderboardResponse.json();
        const latestEvidence: Evidence[] = await evidenceResponse.json();
        if (mounted) {
          setGame(latestGame);
          setChallenges(latestChallenges);
          setLeaderboard(latestLeaderboard);
          setEvidence(latestEvidence);
          if (latestGame.status === 'finished' && !hasNavigatedToResults.current) {
            hasNavigatedToResults.current = true;
            console.log('GAME STATUS FINISHED — navigating to final scoreboard');
            router.replace({
              pathname: '/results',
              params: { code, playerId },
            });
          }
        }
      } catch {
        // Polling errors are intentionally ignored.
      }
    };

    load();
    const intervalId = setInterval(load, 1000);
    return () => {
      mounted = false;
      clearInterval(intervalId);
    };
  }, [code, playerId]);

  useEffect(() => {
    if (game.status === 'finished') {
      setRemainingSeconds(0);
      return;
    }
    if (!game.endTime) {
      return;
    }
    const updateRemaining = () => {
      const seconds = Math.max(0, Math.ceil((Date.parse(game.endTime!) - Date.now()) / 1000));
      setRemainingSeconds(seconds);
    };
    updateRemaining();
    const intervalId = setInterval(updateRemaining, 1000);
    return () => clearInterval(intervalId);
  }, [game.endTime, game.status]);

  const selectedChallenge = useMemo(
    () => challenges.find((challenge) => challenge.id === selectedId) ?? null,
    [challenges, selectedId]
  );
  const currentPlayerScore = leaderboard.find((entry) => entry.playerId === playerId)?.score;
  const liveScore = typeof currentPlayerScore === 'number' && Number.isFinite(currentPlayerScore)
    ? currentPlayerScore
    : 0;

  const actOnChallenge = async (action: 'complete' | 'skip') => {
    if (!code || !playerId || !selectedChallenge) {
      return;
    }
    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/challenges/${encodeURIComponent(selectedChallenge.id)}/${action}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ playerId }),
        }
      );
      if (!response.ok) {
        Alert.alert('Challenge unavailable', 'This challenge is no longer pending.');
        return;
      }
      setSelectedId(null);
    } catch {
      Alert.alert('Could not update challenge', 'Please check your connection and try again.');
    }
  };

  const takePhoto = async () => {
    if (!selectedChallenge || selectedChallenge.state !== 'pending' || game.status === 'finished') {
      return;
    }
    if (!cameraPermission?.granted) {
      const permission = await requestCameraPermission();
      if (!permission.granted) {
        Alert.alert('Camera permission required', 'Allow camera access to take challenge photos.');
        return;
      }
    }
    setCapturedPhotoUri(null);
    setCameraVisible(true);
  };

  const capturePhoto = async () => {
    const photo = await cameraRef.current?.takePictureAsync();
    if (photo?.uri) {
      setCapturedPhotoUri(photo.uri);
    }
  };

  const usePhoto = () => {
    if (!selectedChallenge || !capturedPhotoUri || !code || !playerId || uploadingEvidence) {
      return;
    }
    void submitEvidence(capturedPhotoUri, selectedChallenge);
  };

  const submitEvidence = async (photoUri: string, challenge: Challenge) => {
    setUploadingEvidence(true);
    setUsedPhotoUris((current) => ({ ...current, [challenge.id]: photoUri }));
    setCameraVisible(false);
    const uploadUrl = `${API_BASE_URL}/games/${encodeURIComponent(code ?? '')}/challenges/${encodeURIComponent(challenge.id)}/evidence`;
    try {
      console.log('EVIDENCE UPLOAD START');
      console.log('API URL:', uploadUrl);
      console.log('game code:', code);
      console.log('playerId:', playerId);
      console.log('challengeId:', challenge.id);
      console.log('photo URI:', photoUri);
      if (!photoUri || (!photoUri.startsWith('file://') && !photoUri.startsWith('content://'))) {
        throw new Error('Captured photo URI is missing or is not a file:// or content:// URI');
      }
      const photoResponse = await fetch(photoUri);
      const blob = await photoResponse.blob();
      console.log('EVIDENCE BLOB CREATED', {
        type: blob.type,
        size: blob.size,
      });
      const formData = new FormData();
      formData.append('playerId', playerId ?? '');
      formData.append('image', blob, 'evidence.jpg');
      console.log('EVIDENCE UPLOAD REQUEST', {
        url: uploadUrl,
        playerId,
        challengeId: challenge.id,
      });
      const response = await fetch(
        uploadUrl,
        { method: 'POST', body: formData }
      );
      const responseText = await response.text();
      console.log('EVIDENCE UPLOAD RESPONSE', {
        status: response.status,
        body: responseText,
      });
      console.log('EVIDENCE RESPONSE STATUS:', response.status);
      console.log('EVIDENCE RESPONSE BODY:', responseText);
      if (!response.ok) {
        console.log('EVIDENCE UPLOAD FAILED BEFORE ANALYSIS');
        Alert.alert('Could not submit evidence', 'Try again.');
        return;
      }
      const submitted: Evidence = JSON.parse(responseText);
      console.log('EVIDENCE UPLOAD SUCCESS');
      if (submitted.status === 'submitted' && submitted.aiAnalysis === 'AI analysis failed. You can retry.') {
        console.log('EVIDENCE UPLOAD SUCCEEDED; GEMINI ANALYSIS FAILED');
      }
      setEvidence((current) => [
        ...current.filter((item) => item.evidenceId !== submitted.evidenceId && item.challengeId !== submitted.challengeId),
        submitted,
      ]);
      setSelectedId(null);
    } catch (error) {
      console.log('EVIDENCE UPLOAD TRANSPORT OR RESPONSE PARSE ERROR:', error);
      Alert.alert('Could not submit evidence', 'Try again.');
    } finally {
      setUploadingEvidence(false);
    }
  };

  const retryEvidence = async (item: Evidence) => {
    if (!code || !playerId || uploadingEvidence) {
      return;
    }
    setUploadingEvidence(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/evidence/${encodeURIComponent(item.evidenceId)}/retry`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ playerId }),
        }
      );
      if (!response.ok) {
        Alert.alert('AI analysis failed', 'You can try again later.');
        return;
      }
      const retried: Evidence = await response.json();
      setEvidence((current) => current.map((currentItem) =>
        currentItem.evidenceId === retried.evidenceId ? retried : currentItem
      ));
    } catch {
      Alert.alert('AI analysis failed', 'You can try again later.');
    } finally {
      setUploadingEvidence(false);
    }
  };

  const voteOnEvidence = async (item: Evidence, decision: 'approve' | 'reject') => {
    if (!code || !playerId || item.finalDecision || item.currentVoterDecision) {
      return;
    }
    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/evidence/${encodeURIComponent(item.evidenceId)}/votes`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ evidenceId: item.evidenceId, playerId, decision }),
        }
      );
      if (!response.ok) {
        Alert.alert('Vote unavailable', 'This evidence can no longer be voted on.');
        return;
      }
      const updated: Evidence = await response.json();
      setEvidence((current) => current.map((currentItem) =>
        currentItem.evidenceId === updated.evidenceId ? updated : currentItem
      ));
    } catch {
      Alert.alert('Could not submit vote', 'Please try again.');
    }
  };

  const retakePhoto = () => {
    setCapturedPhotoUri(null);
  };

  const savePhoto = async () => {
    if (!capturedPhotoUri) {
      return;
    }

    try {
      const permission = await MediaLibrary.requestPermissionsAsync();
      if (!permission.granted) {
        Alert.alert(
          'Permission needed',
          'Allow photo access to save this picture to your gallery.'
        );
        return;
      }

      await MediaLibrary.Asset.create(capturedPhotoUri);
      Alert.alert('Photo saved', 'The photo was saved to your gallery.');
    } catch {
      Alert.alert('Could not save photo', 'Please try again.');
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Game</Text>
        {game.status !== 'finished' ? (
          <Text style={styles.timer}>
            {remainingSeconds === null
              ? 'Loading timer...'
              : remainingSeconds === 0
                ? 'Time is up'
                : `${Math.floor(remainingSeconds / 60)}:${String(remainingSeconds % 60).padStart(2, '0')}`}
          </Text>
        ) : null}
      </View>
      <View style={styles.liveScoreCard}>
        <Text style={styles.liveScoreLabel}>YOUR SCORE</Text>
        <View style={styles.liveScoreRow}>
          <Text style={styles.liveScoreValue}>{liveScore}</Text>
          <Text style={styles.liveScoreTotal}>/ 100 PTS</Text>
        </View>
      </View>
      {game.status === 'finished' ? (
        <Text style={styles.finished}>GAME FINISHED</Text>
      ) : (
        <>
          <Text style={styles.sectionTitle}>YOUR CHALLENGES</Text>
          {challenges.map((challenge) => (
        <Pressable
          key={challenge.id}
          style={[styles.card, selectedId === challenge.id && styles.selectedCard]}
          onPress={() => setSelectedId(selectedId === challenge.id ? null : challenge.id)}
        >
          <Text style={styles.challengeText}>{challenge.challenge}</Text>
          <Text style={styles.meta}>
            {challenge.difficulty} · {challenge.points} points · {challenge.category} · {challenge.state}
          </Text>
          {selectedChallenge?.id === challenge.id ? (
            <View style={styles.actions}>
              <Text style={styles.detailText}>{challenge.challenge}</Text>
              {challenge.state === 'pending' ? (
                <>
                  <Pressable style={styles.photoButton} onPress={takePhoto}>
                    <Text style={styles.buttonText}>Take Photo</Text>
                  </Pressable>
                  {usedPhotoUris[challenge.id] ? (
                    <Image source={{ uri: usedPhotoUris[challenge.id] }} style={styles.photoThumbnail} />
                  ) : null}
                  <Pressable style={styles.skipButton} onPress={() => actOnChallenge('skip')}>
                    <Text style={styles.buttonText}>Skip</Text>
                  </Pressable>
                </>
              ) : null}
            </View>
          ) : null}
            </Pressable>
          ))}
        </>
      )}
      {evidence.length > 0 ? (
        <>
          <Text style={styles.sectionTitle}>EVIDENCE & VOTES</Text>
          {evidence.map((item) => (
            <View key={item.evidenceId} style={styles.evidenceBox}>
              <Text style={styles.evidenceTitle}>
                {item.playerName} · {item.challenge}
              </Text>
              <Image source={{ uri: `${API_BASE_URL}${item.imageUrl}` }} style={styles.evidenceImage} />
              {item.status === 'analyzing' ? (
                <View style={styles.analysisState}>
                  <ActivityIndicator color="#A89FFF" />
                  <Text style={styles.analysisText}>AI is reviewing the evidence...</Text>
                </View>
              ) : item.status === 'analyzed' ? (
                <>
                  <Text style={styles.recommendation}>
                    AI recommendation: {item.aiApproved ? 'Approved' : 'Rejected'}
                  </Text>
                  <Text>Confidence: {Math.round((item.aiConfidence ?? 0) * 100)}%</Text>
                  <Text>{item.aiAnalysis}</Text>
                </>
              ) : (
                <>
                  <Text>AI analysis failed. You can retry.</Text>
                  {item.playerId === playerId ? (
                    <Pressable style={styles.retryButton} onPress={() => retryEvidence(item)}>
                      <Text style={styles.buttonText}>Retry Analysis</Text>
                    </Pressable>
                  ) : null}
                </>
              )}
              <Text style={styles.voteProgress}>
                Votes: {item.approveVotes} approve / {item.rejectVotes} reject
                {' '}({item.submittedVoteCount} / {item.totalEligibleVoters})
              </Text>
              {item.finalDecision ? (
                <View style={[
                  styles.finalDecisionCard,
                  item.finalDecision === 'rejected'
                    ? styles.finalDecisionRejectedCard
                    : styles.finalDecisionApprovedCard,
                ]}>
                  <Text style={[
                    styles.finalDecision,
                    item.finalDecision === 'rejected'
                      ? styles.finalDecisionRejected
                      : styles.finalDecisionApproved,
                  ]}>
                    {item.finalDecision === 'approved' ? '✓ APPROVED' : '✕ REJECTED'}
                  </Text>
                  {item.finalDecision === 'rejected' ? (
                    <Text style={styles.finalDecisionRejectedMessage}>
                      The evidence was rejected by the players.
                    </Text>
                  ) : null}
                  <Text>Points awarded: {item.pointsAwarded}</Text>
                </View>
              ) : item.playerId === playerId ? (
                <Text style={styles.ownerNotice}>You cannot vote on your own evidence.</Text>
              ) : item.currentVoterDecision ? (
                <Text style={styles.ownerNotice}>
                  Your vote: {item.currentVoterDecision === 'approve' ? 'Approve' : 'Reject'}
                </Text>
              ) : game.status === 'finished' ? (
                <Text style={styles.ownerNotice}>Voting is closed.</Text>
              ) : (
                <View style={styles.voteActions}>
                  <Pressable style={styles.approveButton} onPress={() => voteOnEvidence(item, 'approve')}>
                    <Text style={styles.buttonText}>Approve</Text>
                  </Pressable>
                  <Pressable style={styles.rejectButton} onPress={() => voteOnEvidence(item, 'reject')}>
                    <Text style={styles.buttonText}>Reject</Text>
                  </Pressable>
                </View>
              )}
              <Text style={styles.aiNotice}>AI recommendation only — final decisions are made later.</Text>
            </View>
          ))}
        </>
      ) : null}
      <Text style={styles.sectionTitle}>LEADERBOARD</Text>
      {leaderboard.map((entry, index) => (
        <View key={entry.playerId} style={styles.leaderboardRow}>
          <Text style={styles.rank}>{index + 1}.</Text>
          <Text style={styles.name}>{entry.name}</Text>
          <Text style={styles.score}>{entry.score} pts</Text>
          <Text style={styles.counts}>
            {entry.completedChallengeCount} completed · {entry.skippedChallengeCount} skipped · {entry.totalChallenges} total
          </Text>
        </View>
      ))}
      <Pressable style={styles.homeButton} onPress={() => router.replace('/')}>
        <Text style={styles.buttonText}>Home</Text>
      </Pressable>
      <Modal visible={cameraVisible} animationType="slide" onRequestClose={() => setCameraVisible(false)}>
        <View style={styles.cameraContainer}>
          {capturedPhotoUri ? (
            <>
              <Image source={{ uri: capturedPhotoUri }} style={styles.cameraPreview} />
              {selectedChallenge ? (
                <Text style={styles.cameraChallengeText}>{selectedChallenge.challenge}</Text>
              ) : null}
              <View style={styles.cameraActions}>
                <Pressable style={styles.skipButton} onPress={retakePhoto}>
                  <Text style={styles.buttonText}>Retake</Text>
                </Pressable>
                <Pressable style={styles.saveButton} onPress={savePhoto}>
                  <Text style={styles.buttonText}>Save Photo</Text>
                </Pressable>
                <Pressable style={styles.completeButton} onPress={usePhoto}>
                  <Text style={styles.buttonText}>{uploadingEvidence ? 'Submitting...' : 'Use Photo'}</Text>
                </Pressable>
              </View>
            </>
          ) : (
            <>
              <CameraView ref={cameraRef} style={styles.camera} facing={cameraFacing} />
              <View style={styles.cameraActions}>
                <Pressable
                  style={styles.skipButton}
                  onPress={() => setCameraFacing((current) => current === 'back' ? 'front' : 'back')}
                >
                  <Text style={styles.buttonText}>Flip Camera</Text>
                </Pressable>
                <Pressable style={styles.completeButton} onPress={capturePhoto} disabled={uploadingEvidence}>
                  <Text style={styles.buttonText}>Take Photo</Text>
                </Pressable>
                <Pressable style={styles.cancelButton} onPress={() => setCameraVisible(false)}>
                  <Text style={styles.cancelButtonText}>Cancel</Text>
                </Pressable>
              </View>
            </>
          )}
        </View>
      </Modal>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#10121B', padding: 20, paddingTop: 56 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { color: '#FFFFFF', fontSize: 30, fontWeight: '900' },
  timer: { color: '#A89FFF', fontSize: 18, fontWeight: '900' },
  liveScoreCard: { backgroundColor: '#1A1E2B', borderColor: '#3B3769', borderRadius: 18, borderWidth: 1, marginTop: 22, padding: 18 },
  liveScoreLabel: { color: '#A89FFF', fontSize: 12, fontWeight: '900', letterSpacing: 1.6 },
  liveScoreRow: { alignItems: 'baseline', flexDirection: 'row', marginTop: 5 },
  liveScoreValue: { color: '#FFFFFF', fontSize: 38, fontWeight: '900', letterSpacing: -1 },
  liveScoreTotal: { color: '#50D6A4', fontSize: 17, fontWeight: '900', marginLeft: 8 },
  finished: { color: '#B14B4B', fontWeight: '700', marginTop: 12 },
  sectionTitle: { color: '#858BA2', fontSize: 12, fontWeight: '900', letterSpacing: 1.5, marginBottom: 10, marginTop: 24 },
  card: { backgroundColor: '#1A1E2B', borderColor: '#30364B', borderRadius: 18, borderWidth: 1, marginBottom: 10, padding: 16 },
  selectedCard: { borderColor: '#786BFF' },
  challengeText: { color: '#FFFFFF', fontSize: 18, fontWeight: '800', lineHeight: 25 },
  meta: { color: '#A9ADBE', marginTop: 8 },
  actions: { borderTopColor: '#E1E4EE', borderTopWidth: 1, marginTop: 12, paddingTop: 12 },
  detailText: { marginBottom: 12 },
  photoButton: { alignItems: 'center', backgroundColor: '#36B88D', borderRadius: 10, marginBottom: 8, padding: 14 },
  photoThumbnail: { borderRadius: 10, height: 120, marginBottom: 8, width: 160 },
  evidenceBox: { backgroundColor: '#1A1E2B', borderColor: '#30364B', borderRadius: 16, borderWidth: 1, marginBottom: 8, padding: 12 },
  evidenceTitle: { color: '#FFFFFF', fontWeight: '800', marginBottom: 8 },
  evidenceImage: { borderRadius: 8, height: 160, marginBottom: 8, width: 220 },
  recommendation: { color: '#50D6A4', fontWeight: '800', marginTop: 4 },
  aiNotice: { color: '#A89FFF', fontSize: 12, fontWeight: '700', marginTop: 8 },
  analysisState: { alignItems: 'center', flexDirection: 'row', gap: 10, paddingVertical: 8 },
  analysisText: { color: '#D9DBE8', fontWeight: '700' },
  retryButton: { alignItems: 'center', backgroundColor: '#777B8B', borderRadius: 10, marginTop: 8, padding: 10 },
  voteProgress: { fontWeight: '700', marginTop: 8 },
  finalDecisionCard: { borderRadius: 12, borderWidth: 1, marginTop: 8, padding: 12 },
  finalDecisionApprovedCard: { backgroundColor: '#17372F', borderColor: '#36B88D' },
  finalDecisionRejectedCard: { backgroundColor: '#3A1E27', borderColor: '#E45D6A' },
  finalDecision: { fontSize: 18, fontWeight: '900' },
  finalDecisionApproved: { color: '#50D6A4' },
  finalDecisionRejected: { color: '#FF6B78' },
  finalDecisionRejectedMessage: { color: '#FFB3B9', fontWeight: '700', marginTop: 5 },
  ownerNotice: { color: '#666A7A', fontWeight: '700', marginTop: 8 },
  voteActions: { flexDirection: 'row', gap: 8, marginTop: 8 },
  approveButton: { alignItems: 'center', backgroundColor: '#36B88D', borderRadius: 10, flex: 1, padding: 10 },
  rejectButton: { alignItems: 'center', backgroundColor: '#E45D6A', borderRadius: 10, flex: 1, padding: 10 },
  completeButton: { alignItems: 'center', backgroundColor: '#786BFF', borderRadius: 10, padding: 12 },
  saveButton: { alignItems: 'center', backgroundColor: '#36B88D', borderRadius: 10, padding: 12 },
  skipButton: { alignItems: 'center', backgroundColor: '#555C73', borderRadius: 10, marginTop: 8, padding: 12 },
  buttonText: { color: '#FFFFFF', fontWeight: '700' },
  cameraContainer: { backgroundColor: '#111111', flex: 1, justifyContent: 'center' },
  camera: { flex: 1 },
  cameraPreview: { flex: 1, resizeMode: 'contain', width: '100%' },
  cameraChallengeText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700', padding: 16 },
  cameraActions: { gap: 8, padding: 16 },
  cancelButton: { alignItems: 'center', borderColor: '#FFFFFF', borderRadius: 10, borderWidth: 1, padding: 12 },
  cancelButtonText: { color: '#FFFFFF', fontWeight: '700' },
  leaderboardRow: { alignItems: 'center', backgroundColor: '#1A1E2B', borderColor: '#30364B', borderRadius: 12, borderWidth: 1, flexDirection: 'row', marginBottom: 8, padding: 12 },
  rank: { color: '#A89FFF', fontWeight: '900', width: 28 },
  name: { color: '#FFFFFF', flex: 1, fontWeight: '800' },
  score: { color: '#50D6A4', fontWeight: '900' },
  counts: { color: '#A9ADBE', fontSize: 11, marginLeft: 8 },
  homeButton: { alignItems: 'center', backgroundColor: '#786BFF', borderRadius: 10, marginTop: 24, padding: 14 },
});
