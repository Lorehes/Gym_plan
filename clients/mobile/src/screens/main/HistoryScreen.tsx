import { Text, View } from 'react-native';

import { screenStyles } from '../_styles';

export function HistoryScreen() {
  return (
    <View style={screenStyles.container}>
      <Text style={screenStyles.title}>기록</Text>
      <Text style={screenStyles.body}>analytics-service 통계 + 세션 히스토리.</Text>
    </View>
  );
}
