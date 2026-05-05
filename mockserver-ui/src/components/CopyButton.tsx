import { useState } from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';

interface CopyButtonProps {
  text: string;
  size?: 'small' | 'medium';
}

export default function CopyButton({ text, size = 'small' }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard API may not be available
    }
  };

  return (
    <Tooltip title={copied ? 'Copied!' : 'Copy'}>
      <IconButton
        size={size}
        onClick={handleCopy}
        sx={{
          opacity: 0.6,
          '&:hover': { opacity: 1 },
          p: '2px',
          '& .MuiSvgIcon-root': { fontSize: '0.875rem' },
        }}
      >
        {copied ? <CheckIcon /> : <ContentCopyIcon />}
      </IconButton>
    </Tooltip>
  );
}
