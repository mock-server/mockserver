import { createTheme } from '@mui/material/styles';
import type { ThemeMode } from './types';

export const logTypeColors = {
  TRACE: 'rgb(215, 216, 154)',
  DEBUG: 'rgb(178, 132, 190)',
  INFO: 'rgb(59, 122, 87)',
  WARN: 'rgb(245, 95, 105)',
  ERROR: 'rgb(179, 97, 122)',
  EXCEPTION: 'rgb(211, 33, 45)',
  CLEARED: 'rgb(139, 146, 52)',
  RETRIEVED: 'rgb(222, 147, 95)',
  UPDATED_EXPECTATION: 'rgb(176, 191, 26)',
  CREATED_EXPECTATION: 'rgb(216, 199, 166)',
  REMOVED_EXPECTATION: 'rgb(124, 185, 232)',
  RECEIVED_REQUEST: 'rgb(114, 160, 193)',
  EXPECTATION_RESPONSE: 'rgb(161, 208, 231)',
  NO_MATCH_RESPONSE: 'rgb(196, 98, 16)',
  EXPECTATION_MATCHED: 'rgb(117, 185, 186)',
  EXPECTATION_NOT_MATCHED: 'rgb(204, 165, 163)',
  VERIFICATION: 'rgb(178, 148, 187)',
  VERIFICATION_FAILED: 'rgb(234, 67, 106)',
  FORWARDED_REQUEST: 'rgb(152, 208, 255)',
  TEMPLATE_GENERATED: 'rgb(241, 186, 27)',
  SERVER_CONFIGURATION: 'rgb(138, 175, 136)',
  DEFAULT: 'rgb(201, 125, 240)',
} as const;

export const becauseColors = {
  matched: 'rgb(107, 199, 118)',
  didntMatch: 'rgb(216, 88, 118)',
  neutral: 'rgb(255, 255, 255)',
} as const;

export function buildTheme(mode: ThemeMode) {
  return createTheme({
    palette: {
      mode,
      ...(mode === 'dark'
        ? {
            background: {
              default: '#121212',
              paper: '#1e1e1e',
            },
            primary: {
              main: '#00bcd4',
            },
            secondary: {
              main: '#ff9800',
            },
          }
        : {
            background: {
              default: '#fafafa',
              paper: '#ffffff',
            },
            primary: {
              main: '#00838f',
            },
            secondary: {
              main: '#e65100',
            },
          }),
    },
    typography: {
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      fontSize: 14,
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            margin: 0,
          },
        },
      },
    },
  });
}
